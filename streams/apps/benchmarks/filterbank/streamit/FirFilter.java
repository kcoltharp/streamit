/*
 *  Copyright 2002 Massachusetts Institute of Technology
 *
 *  Permission to use, copy, modify, distribute, and sell this software and its
 *  documentation for any purpose is hereby granted without fee, provided that
 *  the above copyright notice appear in all copies and that both that
 *  copyright notice and this permission notice appear in supporting
 *  documentation, and that the name of M.I.T. not be used in advertising or
 *  publicity pertaining to distribution of the software without specific,
 *  written prior permission.  M.I.T. makes no representations about the
 *  suitability of this software for any purpose.  It is provided "as is"
 *  without express or implied warranty.
 */


// Together with a delay this creats an FIR 

import streamit.*;

/**
 * Class FirFilter
 *
 * Implements an FIR Filter
 */

public class FirFilter extends Filter {

    int N;
    float COEFF[];

    public FirFilter (float[] COEFF)
    {
        super (COEFF);
    }

    public void init(float[] COEFF) {
	this.N=COEFF.length;
	this.COEFF=new float[COEFF.length];
	
	for (int i=0; i<this.N;i++)
	    this.COEFF[i]=COEFF[i];

	input = new Channel(Float.TYPE, 1, COEFF.length);
	output = new Channel(Float.TYPE, 1);
    }

    public void work(){
	float sum=0;
	for (int i=0; i<N ; i++)
	    sum+=input.peekFloat(i)*COEFF[N-1-i];
	input.pop();
	output.pushFloat(sum);
    }
}

