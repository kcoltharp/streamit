package at.dms.kjc.spacetime;

import at.dms.kjc.*;
import at.dms.util.Utils;


public class RawTile extends ComputeNode {
    private int tileNumber;
    //true if this tile has switch code
    private boolean switches;
    //true if this tile has compute code
    private boolean computes;

    private SwitchCodeStore switchCode;
    private ComputeCodeStore computeCode;

    private IODevice[] ioDevices;
    private String[] ioDevDirection;
    private int numIODevices;

    public RawTile(int row, int col, RawChip rawChip) {
	super(rawChip);
	X = row;
	Y = col;
	setTileNumber();
	computes = false;
	switches = false;
	switchCode = new SwitchCodeStore(this);
	computeCode = new ComputeCodeStore(this);
	numIODevices = 0;
	ioDevices = new IODevice[2];
	ioDevDirection = new String[2];
    }

    public String toString() {
	return "Tile["+X+","+Y+"]";
    }

    public boolean hasIODevice() 
    {
	return (numIODevices > 0);
    }
    
    public void setIODevice(IODevice io) 
    {
	ioDevices[0] = io;
    }

    public void addIODevice(IODevice io, String dir) 
    {
	ioDevices[numIODevices] = io;
	ioDevDirection[numIODevices] = dir;
	numIODevices++;
    }

    public IODevice getIODevice() 
    {
	return ioDevices[0];
    }
    public IODevice getIODevice(int i) 
    {
	return ioDevices[i];
    }

    public IODevice getIODevice(String dir) 
    {
	for (int i = 0; i < numIODevices; i++) {
	    if (ioDevDirection[i].equals(dir)) 
		return ioDevices[i];
	}
	Utils.fail("Cannot find io device in that direction.");
	return null;
    }
    
    
    private void setTileNumber() {
	//because the simulator only simulates 4x4 or 8x8 we
	//have to translate the tile number according to these layouts
	int columns = 4;
	if (rawChip.getYSize() > 4 || rawChip.getXSize() > 4)
	    columns = 8;
	tileNumber = (Y * columns) + X;
    }


    public int getTileNumber() {
	return tileNumber;
    }

    public boolean hasComputeCode() {
	return computes;
    }

    public boolean hasSwitchCode() {
	return switches;
    }

    public SwitchCodeStore getSwitchCode() {
	return switchCode;
    }

    public ComputeCodeStore getComputeCode() {
	return computeCode;
    }

    //this is set by SwitchCodeStore
    public void setSwitches() {
	switches = true;
    }

    //this is set by ComputeCodeStore
    public void setComputes() {
	computes = true;
    }
    
   
}
