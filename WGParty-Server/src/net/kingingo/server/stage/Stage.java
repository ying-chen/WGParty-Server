package net.kingingo.server.stage;

import java.util.ArrayList;
import java.util.HashMap;

import lombok.Getter;
import net.kingingo.server.Main;
import net.kingingo.server.event.EventListener;
import net.kingingo.server.event.EventManager;
import net.kingingo.server.packets.Packet;
import net.kingingo.server.packets.server.CountdownAckPacket;
import net.kingingo.server.stage.stages.Countdown;
import net.kingingo.server.stage.stages.GameStage;
import net.kingingo.server.stage.stages.PlayerChoose;
import net.kingingo.server.stage.stages.WheelStage;
import net.kingingo.server.user.State;
import net.kingingo.server.user.User;

public abstract class Stage implements EventListener, Runnable{
	private static int currentStage = -1;
	private static ArrayList<Class<? extends Stage>> stages_order = new ArrayList<>();
	private static HashMap<Class<? extends Stage>, Stage> stages = new HashMap<Class<? extends Stage>,Stage>();
	
	public void put(Class<? extends Stage> clazz, Stage stage) {
		stages_order.add(clazz);
		stages.put(clazz, stage);
	}
	
	public static void init() {
		new Countdown();
		new PlayerChoose();
		new GameStage();
		new WheelStage();
	}
	
	public static boolean inGame() {
		return !(currentStage() instanceof Countdown);
	}
	
	public static <T extends Stage> T get(Class<T> clazz) {
		return (T) stages.get(clazz);
	}
	
	public static Stage currentStage() {
		int i = 0;
		for(Class<? extends Stage> clazz : stages_order) {
			if(currentStage == i)return Stage.stages.get(clazz);
			i++;
		}
		return null;
	}
	
	public static <T extends Stage> T jump(Class<T> clazz) {
		T stage = (T) stages.get(clazz);
		if(currentStage>=0) {
			currentStage().deactive();
		}
		int index = 0;
		for(Class<? extends Stage> c : stages.keySet()) {
			if(c == clazz)break;
			index++;
		}
		
		currentStage=index;
		stage.start();
		
		return stage;
	}
	
	public static Stage next() {
		if(currentStage == 0) {
			for(User u : User.getAllStats().keySet()) {
				if(u.getState() == State.DASHBOARD_PAGE) {
					u.setState(State.INGAME);
				}
			}
		}
		
		if(currentStage>=0) {
			currentStage().deactive();
		} 
		
		if(currentStage == (stages.size()-1)) {
			currentStage=-1;
			for(User u : User.getAllStats().keySet()) {
				if(u.getState() == State.INGAME) {
					u.setState(State.DASHBOARD_PAGE);
				}
			}
		}
		
		currentStage++;
		Stage s = currentStage();
		s.start();
		return s;
	}
	
	public static void broadcast(Packet packet) {
		User.broadcast(packet, State.INGAME);
	}
	
	@Getter
	private boolean active = false;
	private Thread thread;
	private long timeout;
	private long start;
	
	public Stage(long timeout) {
		this.timeout=timeout;
		EventManager.register(this);
		put(this.getClass(),this);
	}
	
	public void setCountdown(String text) {
		CountdownAckPacket packet = new CountdownAckPacket(System.currentTimeMillis()+this.timeout,text);
		broadcast(packet);
	}
	
	public void run() {
		while(this.active) {
			try {
				this.start = System.currentTimeMillis();
				Thread.sleep(this.timeout);
				if(this.active) {
					boolean b = this.running();
					if(b)Stage.next();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public abstract boolean running();
	
	public void deactive() {
		this.active=false;
	}
	
	public void start() {
		this.active=true;
		this.thread = new Thread(this);
		this.thread.start();
	}
	
	public void printf(String msg) {
		Main.printf("Stage-"+this.getClass().getSimpleName()+"-"+Stage.currentStage, msg);
	}
	
	public String toString() {
		return "Stage-"+this.getClass().getSimpleName()+" active:"+this.isActive();
	}
}