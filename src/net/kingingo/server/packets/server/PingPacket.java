package net.kingingo.server.packets.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.kingingo.server.packets.Packet;

public class PingPacket extends Packet{
	
	public PingPacket() {}
	
	@Override
	public void parseFromInput(DataInputStream in) throws IOException {}

	@Override
	public void writeToOutput(DataOutputStream out) throws IOException {}
	
	public String toString() {
		return this.getPacketName();
	}
}
