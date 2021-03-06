package net.kingingo.server.terminal.commands;

import net.kingingo.server.Main;
import net.kingingo.server.terminal.CommandExecutor;

public class ListCommand implements CommandExecutor{

	@Override
	public void onCommand(String[] args) {
		int size = Main.getServer().getConnections().size();
		switch(size) {
		case 0:
			Main.printf("No Client connected.");
			break;
		case 1:
			Main.printf("One Client is connected.");
			break;
		default:
			Main.printf(size + " Clients are connected!");
		}
	}

	@Override
	public String getDescription() {
		return "Shows how much clients are connected";
	}

}
