/**
 *  StGsmDecoder:  
 *  Decoder portion of GSM Vocoder, java/streamit implementation
 *  Uses GSM Spec 06.10
 *  - J. Wong
 */


import streamit.*;
import java.lang.*;

import java.lang.reflect.*;
import streamit.io.*;
        
class Helper
{
    static short shortify(int a)
    {
	if (a >= 32767)
	{
	    return 32767;
	}
else
	{
	    if (a <= -32768)
	    {
		return -32768;
	    }
	    else
	    {
		return (short) a;
	    }
	}
    }
    
    static short gsm_add(short a, short b)

    {
	long ltmp = (long) a + (long) b;
	if (ltmp >= 32767)
	{
	    return 32767;
	}
	else
	{
	    if (ltmp <= -32768)
	    {
		return -32768;
	    }
	    else
	    {
		return (short) ltmp;
	    }
	}
    }

    static short gsm_sub(short a, short b)
    {
	long ltmp = (long) a - (long) b;
	if (ltmp >= 32767)
	{
	    return 32767;
	}
	else
	{
	    if (ltmp <= -32768)
	    {
		return -32768;
	    }
	    else
	    {
		return (short) ltmp;
	    }
	}
    }

    static short gsm_mult(short a, short b)
    {
	long temp = (long) a * (long) b >> 15;
	if (temp >= 32767)
	{
	    return 32767;
	}
	else
	{
	    if (temp <= -32768)
	    {
		return -32768;
	    }
	    else
	    {
		return (short) temp;
	    }
	}       
    }

    static short gsm_mult_r(short a, short b)
    {
	long temp = ((long) a * (long) b) + 16384;
	short answer = (short) (temp >> 15);
	return answer;
    }

    static short gsm_abs(short a)
    {
	short answer;
	if (a < 0)
	{
	    if (a == -32768)
	    {
		answer = 32767;
	    }
	    else
	    {
		int temp = a * -1;
		if (temp >= 32767)
		{
		    answer = 32767;
		}
		else
		{
		    if (temp <= -32768)
		    {
			answer = -32768;
		    }
		    else
		    {
			answer = (short) temp;
		    }
		}
	    }
	}
	else
	{
	    answer = a;
	}
	return answer;
    }
}
class RPEDecodeFilter extends Filter 
{
    short[] mXmc;
    static short[] FAC = {29218, 26215, 23832, 21846, 20165, 18725, 17476, 16384};
    short[] xmp;
    short[] ep;

    public void init() 
    {
	input = new Channel(Short.TYPE, 15);
	output = new Channel(Short.TYPE, 40);
	mXmc = new short[13];   //mSequence
	//others are in work() method	
	xmp = new short[13]; //intermediary
	ep = new short[40];  //output
    }

    public void work() 
    {
	/**
	 *  Inputs to RPEDecodeFilter: Xmaxc - Maximum value of the 13 bits, 
	 *                     Xmc - 13 bit sample
	 *                     Mc - 2 bit grid positioning
	 */
	
	for (short i = 0; i < 13; i++)
	{
	    mXmc[i] = input.popShort();
	}
	short xmaxc = input.popShort();    //mRpeMagnitude
	short mc = input.popShort();       //mRpeGridPosition

	 
	//Get the exponent, mantissa of Xmaxc:
       
	short exp = 0;
	if (xmaxc > 15)
	    {
		exp = Helper.gsm_sub(Helper.shortify(xmaxc >> 3), (short) 1);
	    }
	short mant = Helper.gsm_sub(xmaxc, Helper.shortify(exp << 3));
	
	//normalize mantissa 0 <= mant <= 7;
	
	if (mant == 0)
	    {
		exp = -4;
		mant = 7;
	    }
	else
	    {
		while (mant <= 7) 
		    {
			mant = Helper.shortify(mant << 1 | 1);
			exp--;
		    }
		mant = Helper.gsm_sub(mant, (short) 8);
	    }
	
	/* 
	 *  This part is for decoding the RPE sequence of coded Xmc[0..12]
	 *  samples to obtain the xMp[0..12] array.  Table 4.6 is used to get
	 *  the mantissa of xmaxc (FAC[0..7]).
	 */
	
	short temp;
	short temp1 = FAC[mant];
	short temp2 = Helper.gsm_sub((short) 6, exp);
	short temp3 = Helper.shortify(1 << Helper.gsm_sub(temp2, (short) 1));

	for (short i = 0; i < 13; i++)
	    {
		temp = Helper.gsm_sub(Helper.shortify(mXmc[i] << 1), (short) 7);    //3 bit unsigned
		temp <<= 12;                 //16 bit signed
		temp = Helper.gsm_mult_r(temp1, temp);
		temp = Helper.gsm_add(temp, temp3);
		xmp[i] = Helper.shortify(temp >> temp2);
	    }

	/**
	 *  This procedure computes the reconstructed long term residual signal
	 *  ep[0..39] for the LTP analysis filter.  The inputs are the Mc
	 *  which is the grid position selection and the xMp[0..12] decoded
	 *  RPE samples which are upsampled by a factor of 3 by inserting zero
	 *  values.
	 */
         //output!

	for(short k = 0; k < 40; k++)
	    {
		ep[k] = 0;
	    }
	for(short i = 0; i < 12; i++)
	    {
		//System.out.println("accessing " + (mc+(3*i)));
		ep[mc + (3 * i)] = xmp[i];
	    } 
	//output the sucker!
	for (short i = 0; i < ep.length; i++)
	{
	    output.pushShort(ep[i]);
	}
	//System.err.println("Got to RPEDecode!");
    }
}

class LTPFilter extends Filter 
{
    static short[] QLB = {3277, 11469, 21299, 32767};
    short[] drp;
    short nrp;
   
    public void init() 
    {
	input = new Channel(Short.TYPE, 162);
	output = new Channel(Short.TYPE, 1);
	drp = new short[160];
	nrp = 40;   //initial condition
    }

    public void work()  //output:  drpp
    {
	short mBcr = input.popShort();  //mLtpGain
	short mNcr = input.popShort();  //mLtpOffset
	//do it!
	for (short i = 0; i < 160; i++)
	{
	    drp[i] = input.popShort(); //drp from AdditionUpdateFilter
	}
	short nr = mNcr;
	if ((mNcr < 40) || (mNcr > 120))
	{
	    nr = nrp;
	}
	nrp = nr;

	//Decoding of the LTP gain mBcr
	short brp = QLB[mBcr];
	short drpp = 1;
	for (short i = 121; i < 161; i++)
	{
	    drpp = Helper.gsm_mult_r(brp, drp[i - nr]);   //delay and multiply operations
	} 
	
	output.pushShort(drpp);   
	//System.err.println("Got to LTPFilter!");
    }
}

class AdditionUpdateFilter extends Filter 
{       
    short[] ep;
    short[] drp;
    public void init() 
    {
	input = new Channel(Short.TYPE, 41);
	output = new Channel(Short.TYPE, 160);
	ep = new short[40]; //input
	drp = new short[160];  //output
	for (short i = 0; i < drp.length; i++)
	{
	    drp[i] = 0;   //initial conditions
	}
    }

    public void work() 
    {
	//get inputs:
	for (short i = 0; i < 40; i++)
	{
	    ep[i] = input.popShort();
	}
	short drpp = input.popShort();

	for (short j = 121; j < 160; j++)  //add part!
	{
	    drp[j] = Helper.gsm_add(ep[j - 121], drpp);
	}
	for (short k = 0; k < 120; k++)    //update part!
	{
	    drp[k] = drp[k + 40];
	}
	
	for (short i = 0; i < drp.length; i++)  //output!
	{
	    output.pushShort(drp[i]);
	}
	//System.err.println("Got to Addition Update Filter!");
    }
}

class ShortTermSynthFilter extends Filter 
{

    static short[] INVA = {13107, 13107, 13107, 13107, 19223, 17476, 31454, 29708};
    static short[] MIC = {-32, -32, -16, -16, -8, -8, -4, -4};
    static short[] B = {0, 0, 2048, -2560, 94, -1792, -341, -1144};
    
    short[] mdrpin; //input
    short[] mdrp;   //shortened input
    short[] mLARc;  //input
    short[] mLARpp; //intermediary
    short[] mprevLARpp; //intermediary
    short[] mLARp;  //intermediary
    short[] mrrp; //intermediary
    short[] wt; //temporary array
    short[] v; //temporary array
    short[] sr; //output!

    public void init() 
    {
	input = new Channel(Short.TYPE, 168);
	output = new Channel(Short.TYPE, 160);
	mdrpin = new short[160];
	mdrp = new short[40];
	mLARc = new short[8];
	mLARpp = new short[8];
	mprevLARpp = new short[8];
	for(short i = 0; i < mprevLARpp.length; i++)
	{
	    mprevLARpp[i] = 0;
	}
	mLARp = new short[8];
	mrrp = new short[8];
	wt = new short[160];
	v = new short[9];
	for (short i = 0; i < v.length; i++)
	{
	    v[i] = 0;
	}
	sr = new short[160];
    }

    public void work() 
    {
	for (short i = 0; i < mdrpin.length; i++)
	{
	    mdrpin[i] = input.popShort();
	}
	//truncate to only get mdrpin[120...159]
	for (int i = 0; i < mdrp.length; i++)
	{
	    mdrp[i] = mdrpin[i + 120];
	}
	for (short i = 0; i < mLARc.length; i++)
	{
	    mLARc[i] = input.popShort();   //fix inputs!!
	}
	
	
	//Decoding of the coded Log-Area ratios:
	for (short i = 0; i < 8; i++)
	{
	    short temp1 = Helper.shortify((Helper.gsm_add(mLARc[i], MIC[i])) << 10);
	    short temp2 = Helper.shortify(B[i] << 10);
	    temp1 = Helper.gsm_sub(temp1, temp2);
	    temp1 = Helper.gsm_mult_r(INVA[i], temp1);
	    mLARpp[i] = Helper.gsm_add(temp1, temp1);
	}
	    //Computation of the quantized reflection coefficients

	//Interpolation of mLARpp to get mLARp:
	for (short k = 0; k < 13; k++)
	{
	    for(short i = 0; i < 8; i++)
	    {
		mLARp[i] = Helper.gsm_add(Helper.shortify(mprevLARpp[i] >> 2), Helper.shortify(mLARpp[i] >> 2));
		mLARp[i] = Helper.gsm_add(mLARp[i],  Helper.shortify(mprevLARpp[i] >> 1));
	    }
	}
	for (short k = 13; k < 27; k++)
	{
	    for (short i = 0; i < 8; i++)
	    {
		mLARp[i] = Helper.gsm_add(Helper.shortify(mprevLARpp[i] >> 1), Helper.shortify(mLARpp[i] >> 1));
	    }
	}
	for (short k = 27; k < 39; k++)
        {
	    for (short i = 0; i < 8; i++)
	    {
		mLARp[i] = Helper.gsm_add(Helper.shortify(mprevLARpp[i] >> 2), Helper.shortify(mLARpp[i] >> 2));
		mLARp[i] = Helper.gsm_add(mLARp[i], Helper.shortify(mLARpp[i] >> 1));
	    }
	}
	for (short k = 40; k < 160; k++)
	{
	    for (short i = 0; i < 8; i++)
	    {
		mLARp[i] = mLARpp[i];
	    }
	}
	//set current LARpp to previous:
	for (short j = 0; j < mprevLARpp.length; j++)
	{
	    mprevLARpp[j] = mLARpp[j];
	}

	//Compute mrrp[0..7] from mLARp[0...7]
	for (short i = 0; i < 8; i++)
	{
	    short temp = Helper.gsm_abs(mLARp[i]);
	    if (temp < 11059)
	    {
		temp = Helper.shortify(temp << 1);
	    }
	    else 
	    {
		if (temp < 20070)
		{
		    temp = Helper.gsm_add(temp, (short) 11059);
		}
		else
		{
		    temp = Helper.gsm_add((short) (temp >> 2), (short) 26112);
		}
	    }
	    mrrp[i] = temp;
	    if (mLARp[i] < 0)
	    {
		mrrp[i] = Helper.gsm_sub((short) 0, mrrp[i]);
	    }
	}

	//Short term synthesis filtering:  uses drp[0..39] and rrp[0...7] 
	// to produce sr[0...159].  A temporary array wt[0..159] is used.
	for (short k = 0; k < 40; k++)
	{
	    wt[k] = mdrp[k];
	}
	for (short k = 0; k < 40; k++)
	{
	    wt[40+k] = mdrp[k];
	}
	for (short k = 0; k < 40; k++)
	{
	    wt[80+k] = mdrp[k];
	}
	for (short k = 0; k < 40; k++)
	{
	    wt[120+k] = mdrp[k];
	}
	//below is supposed to be from index_start to index_end...how is
	//this different from just 0 to 159?
	for (short k = 0; k < 13; k++)
	{
	    short sri = wt[k];
	    for (short i = 1; i < 8; i++)
	    {
		sri = Helper.gsm_sub(sri, Helper.gsm_mult(mrrp[8-i], v[8-i]));
		v[9-i] = Helper.gsm_add(v[8-i], Helper.gsm_mult_r(mrrp[8-i], sri));
	    }
	    sr[k] = sri;
	    v[0] = sri;
	}

	for (short k = 13; k < 27; k++)
	{
	    short sri = wt[k];
	    for (short i = 1; i < 8; i++)
	    {
		sri = Helper.gsm_sub(sri, Helper.gsm_mult(mrrp[8-i], v[8-i]));
		v[9-i] = Helper.gsm_add(v[8-i], Helper.gsm_mult_r(mrrp[8-i], sri));
	    }
	    sr[k] = sri;
	    v[0] = sri;
	}

	for (short k = 27; k < 40; k++)
	{
	    short sri = wt[k];
	    for (short i = 1; i < 8; i++)
	    {
		sri = Helper.gsm_sub(sri, Helper.gsm_mult(mrrp[8-i], v[8-i]));
		v[9-i] = Helper.gsm_add(v[8-i], Helper.gsm_mult_r(mrrp[8-i], sri));
	    }
	    sr[k] = sri;
	    v[0] = sri;
	}	

	for (short k = 40; k < 160; k++)
	{
	    short sri = wt[k];
	    for (short i = 1; i < 8; i++)
	    {
		sri = Helper.gsm_sub(sri, Helper.gsm_mult(mrrp[8-i], v[8-i]));
		v[9-i] = Helper.gsm_add(v[8-i], Helper.gsm_mult_r(mrrp[8-i], sri));
	    }
	    sr[k] = sri;
	    v[0] = sri;
	}

	for (short j = 0; j < sr.length; j++)
	{
	    output.pushShort(sr[j]);
	}
	//System.err.println("Got to ShortTermSynth Filter!");
    }
}

class PostProcessingFilter extends Filter 
{
    short[] mSr;  //input
    short[] sro; //output of PostProcessing, input to upscaling/truncation
    short[] srop; //output
    short msr;


    public void init() 
    {
	input = new Channel(Short.TYPE, 160);
	output = new Channel(Short.TYPE, 160);
	mSr = new short[160];
	sro = new short[160];
	srop = new short[160];
	msr = 0; //initial condition
    }

    public void work() 
    {
	for (short i = 0; i < mSr.length; i++)
	{
	    mSr[i] = input.popShort();
	}

	//De-emphasis filtering!
	for (short k = 0; k < mSr.length; k++)
	{
	    short temp = Helper.gsm_add(mSr[k], Helper.gsm_mult_r(msr, (short) 28180));
	    msr = temp;
	    sro[k] = msr;
	}

	//upscaling of output signal:
	for (short k = 0; k < srop.length; k++)
	{
	    srop[k] = Helper.gsm_add(sro[k], sro[k]);
	}

	//truncation of the output variable:
	for (short k = 0; k < srop.length; k++)
	{
	    srop[k] = Helper.shortify(srop[k] / 8);
	    srop[k] = Helper.gsm_mult(srop[k], (short) 8);
	}
	
	for (int a = 0; a < srop.length; a++)
	{
	    if(a == srop.length - 1)
	    {
		//System.out.println("Running last iteration of PostProcess!");
	    }
	    output.pushShort(srop[a]);
	}
    }
}

class DecoderFeedback extends FeedbackLoop
{
    public void init()
    {
	this.setDelay(1);
	this.setJoiner(WEIGHTED_ROUND_ROBIN (40, 1));  //sequence: ep[0....39], drpp
  	this.setBody(new AdditionUpdateFilter());
	this.setSplitter(DUPLICATE ());   
	//note:  although drp[120...159] are all that are
	//       required for ShortTermSynth, this is currently
	//       the simplest way to implement things, theinput will be filtered internally.
 	this.setLoop(new LTPLoopStream());
    }

    public short initPathShort(int index)
    {
	return 0;
    }
	
}

class LTPLoopStream extends Pipeline
{
    public void init()
    {
	this.add(new LTPInputSplitJoin());
	this.add(new LTPFilter());
    }
}

class LTPInputSplitJoin extends SplitJoin
{
    public void init()
    {
	this.setSplitter(WEIGHTED_ROUND_ROBIN (0, 1));
	this.add(new LTPPipeline());
	this.add(new Identity(Short.TYPE));
	this.setJoiner(WEIGHTED_ROUND_ROBIN(2, 160)); //bcr, ncr, drp[0...159]
    }
}


class LARInputSplitJoin extends SplitJoin
{
    public void init()
    {
	this.setSplitter(WEIGHTED_ROUND_ROBIN (1, 0));  //we don't care about it going to in2
	this.add(new Identity(Short.TYPE));
	this.add(new LARPipeline());	
	this.setJoiner(WEIGHTED_ROUND_ROBIN(160, 8));  //drp[0...160], LARc[0...7];
    }
}

class LTPPipeline extends Pipeline
{
    public void init()
    {
	this.add(new FileReader("BinaryDecoderInput1", Short.TYPE));
	this.add(new LTPInputFilter());
    }
}
class LARPipeline extends Pipeline
{
    public void init()
    {
	this.add(new FileReader("BinaryDecoderInput1", Short.TYPE));
	this.add(new LARInputFilter());
    }
}

class RPEInputFilter extends Filter
{
    //order of output: mSequence[0..13], mRpeMagnitude, mRpeGridPosition
    short[] mdata;
    short[] single_frame;
    boolean donepushing;

#include "DecoderInput.java"
    
    public void init()
    {
	mdata = new short[151840];
	single_frame = new short[260];
	input = new Channel(Short.TYPE, 151840);
	output = new Channel(Short.TYPE, 60); 
	donepushing = false;
    }

    public void work()
    {
	//System.err.println("I get here!!!");
	for (int i = 0; i < mdata.length; i++)
	{
	    mdata[i] = input.popShort();
	}

	if (donepushing)
	{
	    //AssertedClass.SERROR("Done Pushing at RPEInputFilter!");
	}

	int frame_index = 0;
	for (int j = 0; j < 584; j++)  //only pushing one in for now, should be 0 to 584
	  {
	    for (int k = 0; k < single_frame.length; k++)
	      {
		single_frame[k] = mdata[frame_index + k];
	      }
	    getParameters(single_frame);
	    frame_index += 260;
	  
	  
	    //now, push the stuff on!
	    for (int i = 0; i < 4; i++)
	    {
		for (int a = 0; a < 13; a++)
		{
		    output.pushShort(mSequence[i][a]);
		}
		output.pushShort(mRpeMagnitude[i]);
		output.pushShort(mRpeGridPosition[i]);
	    }
	    donepushing = true;
	  }
	//System.err.println("RPE Input Filter yeah!");
    }
}
class LARInputFilter extends Filter
{
    //order of output: mLarParameters[0...8]
    short[] mdata;
    short[] single_frame;
    boolean donepushing;

#include "DecoderInput.java"

    public void init()
    {
	mdata = new short[151840];
	single_frame = new short[260];
	input = new Channel(Short.TYPE, 151840);
	output = new Channel(Short.TYPE, 8); 
	donepushing = false;
    }

    public void work()
    {
	for (int i = 0; i < mdata.length; i++)
	{
	    mdata[i] = input.popShort();
	}

	if (donepushing)
	{
	    //AssertedClass.SERROR("Done Pushing at LARInputFilter!");
	}
	int frame_index = 0;
	for (int j = 0; j < 584; j++)  //only pushing one in for now, should be 0 to 584
	  {
	    for (int k = 0; k < single_frame.length; k++)
	      {
		single_frame[k] = mdata[frame_index + k];
	      }
	    getParameters(single_frame);
	    frame_index += 260;
	  
	
	    //now, push the stuff on!
	    for (int i = 0; i < 8; i++)
	    {
		output.pushShort(mLarParameters[i]);
	    }
	    donepushing = true;
	  }
	//System.err.println("LARinputFilter gooo!");

    }
}
class LTPInputFilter extends Filter
{
    //order of output: mLtpGain[4], mLtpOffset[4]
    short[] mdata;
    short[] single_frame;
    boolean donepushing;

#include "DecoderInput.java"

    public void init()
    {
	mdata = new short[151840];
	single_frame = new short[260];
	input = new Channel(Short.TYPE, 151840);
	output = new Channel(Short.TYPE, 8);
	donepushing = false;
    }

    public void work()
    {
	for (int i = 0; i < mdata.length; i++)
	{
	    mdata[i] = input.popShort();
	}

	if (donepushing)
	{
	    //AssertedClass.SERROR("Done Pushing at LTPInputFilter!");
	}
	int frame_index = 0;
	for (int j = 0; j < 584; j++)  //only pushing one in for now, should be 0 to 584
	  {
	    for (int k = 0; k < single_frame.length; k++)
	      {
		single_frame[k] = mdata[frame_index + k];
	      }
	    getParameters(single_frame);
	    frame_index += 260;
	  	  
	  
	    //now, push the stuff on!
	    for (int i = 0; i < 4; i++)
	    {
		output.pushShort(mLtpGain[i]);
		output.pushShort(mLtpOffset[i]);
	    }
	  }
	donepushing = true;
	//System.err.println("LTP Input filter gooooo!");
    }
}

/**
 *  Temporary addition:  
 *  takes in short[160] array, pushes out 40 bits at a time 
 *  so that LAR filtering is only applied after all four subframes
 *  are processed.
 */
class HoldFilter extends Filter
{
    short[] mDrp;    

    public void init()
    {
	input = new Channel(Short.TYPE, 160);
	output = new Channel(Short.TYPE, 40);
	mDrp = new short[160];
    }

    public void work()
    {
	for (int i = 0; i < mDrp.length; i++)
	{
	    mDrp[i] = input.popShort();
	}
	for (int j = 0; j < 40; j++)
	{
	    output.pushShort(mDrp[j + 120]);
	}
	//System.err.println("Hold filter go!");
    }
    
}
public class StGsmDecoder extends StreamIt 
{
    //include variables for parsing here!
    public static void main(String args[]) 
    {
	new StGsmDecoder().run(args); 
	
    }

    public void init() {
	
	this.add(new FileReader("BinaryDecoderInput1", Short.TYPE));
	this.add(new RPEInputFilter());
	this.add(new RPEDecodeFilter());
	this.add(new DecoderFeedback());
	this.add(new HoldFilter());
	this.add(new LARInputSplitJoin());
	this.add(new ShortTermSynthFilter());
	this.add(new PostProcessingFilter());
	this.add(new streamit.io.FileWriter("blahblah", Short.TYPE));	
    }
}
 






