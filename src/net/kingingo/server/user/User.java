package net.kingingo.server.user;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.java_websocket.WebSocket;
import org.java_websocket.enums.ReadyState;

import lombok.Getter;
import lombok.Setter;
import net.kingingo.server.Main;
import net.kingingo.server.event.EventManager;
import net.kingingo.server.event.events.SpectateChangeEvent;
import net.kingingo.server.event.events.StateChangeEvent;
import net.kingingo.server.mysql.MySQL;
import net.kingingo.server.packets.Packet;
import net.kingingo.server.packets.client.HandshakePacket;
import net.kingingo.server.packets.client.RegisterPacket;
import net.kingingo.server.packets.server.HandshakeAckPacket;
import net.kingingo.server.packets.server.StatsAckPacket;
import net.kingingo.server.utils.Callback;
import net.kingingo.server.utils.Utils;

public class User {
	public static final double ALPHA = 0.125;
	@Getter
	private static HashMap<WebSocket, User> users = new HashMap<WebSocket, User>();
	private static HashMap<UUID, User> uuids = new HashMap<UUID, User>();
	private static HashMap<User, UserStats> stats = new HashMap<User, UserStats>();
	
	public static void broadcast(Packet packet) {
		broadcast(packet, null);
	}
	

	public static void broadcast(Packet packet,State st) {
		broadcast(packet, st, null);
	}
	
	public static void broadcast(Packet packet,State st, List<User> blackList) {
		for(User u : users.values()) {
			if(st == null || st == u.getState()) {
				if(blackList == null || !blackList.contains(u)) {
					u.write(packet);
				}
			}
		}
	}
	
	public static void createTestUsers() {
		createTestUser("Moritz",8,3);
		createTestUser("Oskar",7,4);
		createTestUser("Henrik",6,5);
		createTestUser("Jonathan",5,6);
		createTestUser("Jonas",4,7);
		createTestUser("Leon",10,0);
	}
	
	public static User createTestUser(String name,int wins,int loses) {
		User u = new User(null);
		u.uuid = UUID.nameUUIDFromBytes(name.getBytes());
		u.setName(name);
		stats.put(u, new UserStats(u));
		u.getStats().add("loses", loses);
		u.getStats().add("wins", wins);
		return u;
	}
	
	public static HashMap<User, UserStats> getAllStats(){
		return stats;
	}
	
	public static User getUser(String name) {
		for(User u : stats.keySet()) {
			if(u.getName().equalsIgnoreCase(name))return u;
		}
		return null;
	}

	public static User getUser(UUID uuid) {
		return uuids.get(uuid);
	}

	public static User getUser(WebSocket webSocket) {
		return getUsers().get(webSocket);
	}

	private long timeDifference = 0;
	
	@Getter
	private WebSocket socket;
	
	@Setter
	@Getter
	private String name;
	@Getter
	private UUID uuid;
	
	@Getter
	private State state = State.HANDSHAKE;
	@Getter
	@Setter
	private long offline=0;
	
	private long SampleRTT=0; //Round Trip Time
	private long estimatedRTT=0;
	
	public User(WebSocket webSocket) {
		this.socket = webSocket;
		if(webSocket!=null)User.getUsers().put(webSocket, this);
	} 
	
	public long getTimeDifference() {
		return this.timeDifference+this.SampleRTT;
	}
	
	public void setState(State state) {
		StateChangeEvent ev = new StateChangeEvent(this, this.state, state);
		this.state=state;
		EventManager.callEvent(ev);
	}
	
	public boolean isOnline() {
		return this.state != State.OFFLINE;
	}	
	
	public long getRTT() {
		return this.SampleRTT;
	}
	
	public void setTimeDifference(long time) {
		time -= - getRTT();
		if(!isOnline())return;
		if(timeDifference == 0) {
			timeDifference = time;
		}else {
			this.timeDifference = (long) ((1-ALPHA) * time + ALPHA * this.timeDifference);
		}
	}
	
	public void RTT() {
		if(!isOnline())return;
		if(estimatedRTT==0) {
			this.estimatedRTT = System.currentTimeMillis();
		} else {
			this.estimatedRTT = System.currentTimeMillis() - this.estimatedRTT;
//			Main.debug("Old SampleRTT:"+this.SampleRTT + " estimated RTT:"+this.estimatedRTT);
			this.SampleRTT=(this.SampleRTT == 0 ? this.estimatedRTT : (long) ((1-ALPHA)*this.estimatedRTT+ALPHA*this.SampleRTT));
			this.estimatedRTT=0;
//			Main.debug("new SampleRTT:"+this.SampleRTT);
		}
	}
	
	public UserStats getStats() {
		return stats.get(this);
	}

	public void init(String name) {
		this.name = name;
	}
	
	public boolean isUnknown() {
		return this.uuid == null;
	}

	public boolean isTester() {
		return this.getSocket()==null;
	}
	
	public UUID register(RegisterPacket packet) {
		this.uuid = UUID.randomUUID();
		this.name = packet.getName();
		setState(State.REGISTER_PAGE);
		User.stats.put(this, new UserStats(this));
		User.uuids.put(this.uuid, this);
		try {
			Utils.createDirectorie(getPath());
			
			Utils.toFile(getOriginalPath(packet.format), packet.getImage());
			Utils.resize(new File(getOriginalPath(packet.format)), getPath(),256,256);
			
			Main.printf("UUID:"+uuid.toString()+"("+uuid.toString().length()+") "+name+" format:"+packet.getFormat());
			MySQL.Update("INSERT INTO users (uuid,name) VALUES ('" + uuid.toString() + "','" + name + "');");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		HashMap<User,UserStats> s = new HashMap<User,UserStats>();
		s.put(this, getStats());
		
		StatsAckPacket stats = new StatsAckPacket(s);
		State state;
		for(User user : User.users.values()) {
			state = user.getState();
			if(state == State.DASHBOARD_PAGE && user.getUuid() != this.uuid) {
				user.write(stats);
			}
		}
		return uuid;
	}

	public String getOriginalPath(String format) {
		return Main.WEBSERVER_PATH + File.separatorChar + "images"+File.separatorChar+"profiles"+File.separatorChar+"original"+File.separatorChar+getUuid().toString()+"."+format;
	}
	
	public String getPath() {
		return Main.WEBSERVER_PATH + File.separatorChar + "images"+File.separatorChar+"profiles"+File.separatorChar+"resize"+File.separatorChar+getUuid().toString()+".jpg";
	}

	public void write(Packet packet) {
		if(isTester())return;
		if(!isOnline() && !(packet instanceof HandshakeAckPacket))return;
		if(!getSocket().isOpen() || getSocket().isClosed())return;
		if(getSocket().getReadyState() != ReadyState.OPEN) {
			Main.debug("can't send "+toString()+" "+packet.getPacketName()+" ReadyStage:"+getSocket().getReadyState().toString());
		}else Main.getServer().write(this, packet);
	}
	
	public void setSocket(WebSocket socket) {
		User.users.remove(this.socket);
		this.socket=socket;
		User.users.put(this.socket,this);
	}

	public User load(HandshakePacket packet) {
		UUID uuid = packet.getUuid();
		User found = User.getUser(uuid);
		
		if(found!=null) {
			Main.debug("User already loaded "+found.toString());
			remove();
			found.setSocket(this.socket);
			found.write(new HandshakeAckPacket(found.getName(), true));
			found.setState(packet.getState());
			return found;
		}
		this.uuid = uuid;

		Main.debug("Loading User "+toString());

		final User user = this;
		MySQL.asyncQuery("SELECT * FROM users WHERE uuid='" + this.uuid.toString() + "';", new Callback<ResultSet>() {

			@Override
			public void run(ResultSet rs) {
				try {
					int count = 0;
					while(rs.next()) {
						count++;
						user.setName(rs.getString("name"));
						User.stats.put(user, new UserStats(user));
					}
					
					if (count == 1 && user.getName() != null) {
						User.uuids.put(user.uuid, user);
						user.write(new HandshakeAckPacket(user.getName(), true));
					} else {
						user.write(new HandshakeAckPacket(false));
					}
					Main.debug("User: "+user.toString()+" Loaded:"+count+" -> "+(count==1 ? "accepted" : "not accepted"));
					user.setState(packet.getState());
				} catch (SQLException e) {
					e.printStackTrace();
				} 
			}
		});
		
		return user;
	}
	
	public void remove() {
		if(this.uuid!=null)User.uuids.remove(this.uuid);
		User.stats.remove(this);
		User.users.remove(this.socket);
	}

	public String getDetails() {
		StringBuilder builder = new StringBuilder();
		builder.append("\n"+(isTester() ? "Tester" : "User") + " " +getName()+" - "+getUuid().toString()+"\n");
		if(!isTester()) {
			builder.append("	state: " + getState().name() + (isOnline()?"":" since "+getOffline()+"ms")+"\n");
			builder.append("	time difference: "+this.timeDifference+"\n");
			builder.append("	RTT: "+getRTT()+"\n");
		}
		builder.append(getStats().toString()+"\n");
		
		return builder.toString();
	}
	
	public boolean equalsUUID(User u) {
		if(u==null)return false;
		return u.getUuid().toString().equalsIgnoreCase(getUuid().toString());
	}
	
	public String toString() {
		return (name == null ? (this.uuid == null ? "UNKOWN" : this.uuid.toString()) : name.toUpperCase());
	}
}
