package net.kingingo.server.games;

import lombok.Getter;
import net.kingingo.server.Main;
import net.kingingo.server.event.EventHandler;
import net.kingingo.server.event.EventListener;
import net.kingingo.server.event.EventManager;
import net.kingingo.server.event.events.PacketReceiveEvent;
import net.kingingo.server.event.events.StateChangeEvent;
import net.kingingo.server.packets.Packet;
import net.kingingo.server.packets.client.games.GameEndPacket;
import net.kingingo.server.packets.client.games.GameStartAckPacket;
import net.kingingo.server.packets.client.higherlower.HigherLowerSearchChoosePacket;
import net.kingingo.server.packets.server.games.GameStartPacket;
import net.kingingo.server.packets.server.higherlower.HigherLowerAnsweredPacket;
import net.kingingo.server.packets.server.higherlower.HigherLowerSearchPacket;
import net.kingingo.server.stage.Stage;
import net.kingingo.server.user.State;
import net.kingingo.server.user.User;
import net.kingingo.server.utils.Callback;

public abstract class Game implements EventListener{
	@Getter
	private User user1;
	protected boolean user1_done=false;
	@Getter
	private User user2;
	protected boolean user2_done=false;
	@Getter
	private boolean active = false;
	private Callback<User[]> endCallback;
	protected int user1_score = 0;
	protected int user2_score = 0;
	
	public Game(Callback<User[]> endCallback) {
		EventManager.register(this);
		this.endCallback=endCallback;
	}
	
	public void broadcast(Packet packet) {
		writeU1(packet);
		writeU2(packet);
	}
	
	public void writeU1(Packet packet) {
		this.user1.write(packet);
	}
	
	public void writeU2(Packet packet) {
		this.user2.write(packet);
	}
	
	public void start(User u1, User u2) {
		reset();
		this.active=true;
		this.user1=u1;
		this.user2=u2;
		
		GameStartPacket packet = new GameStartPacket(getName().toLowerCase());
		Stage.broadcast(packet);
	}

	public void reset() {
		this.user1_score=0;
		this.user2_score=0;
	}
	
	public void end() {
		User win = null;
		User lose = null;
		
		if(this.user1_score > this.user2_score) {
			win = this.getUser1();
			lose = this.getUser2();
		}else if(this.user1_score < this.user2_score){
			win = this.getUser2();
			lose = this.getUser1();
		}else{
			win = null;
			lose = null;
		}
		print("END -> win:"+win+" lose:"+lose);
		
		end(win,lose);
	}
	
	public void end(User win, User lose) {
		this.active=false;
		this.user1_done=false;
		this.user2_done=false;
		this.endCallback.run((win==null&&lose==null ? null : new User[] {win,lose}));
		reset();
	}

	public User getOther(User u) {
		return u.equalsUUID(this.getUser1()) ? this.getUser2() : this.getUser1();
	}
	
	@EventHandler
	public void change(StateChangeEvent ev) {
		if(!isActive())return;
		if(ev.getNewState() == State.OFFLINE) {
			if(ev.getUser().equalsUUID(getUser1())) {
				end(getUser2(),getUser1());
			}else if( ev.getUser().equalsUUID(getUser2())) {
				end(getUser1(),getUser2());
			}
		}
	}
	
	public String getName() {
		return this.getClass().getSimpleName();
	}
	
	public void print(String msg) {
		Main.printf(getName(), msg);
	}
}
