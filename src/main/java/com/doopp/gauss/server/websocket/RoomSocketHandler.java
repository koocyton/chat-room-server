package com.doopp.gauss.server.websocket;

import com.alibaba.fastjson.JSONObject;
import com.doopp.gauss.api.entity.RoomEntity;
import com.doopp.gauss.api.entity.UserEntity;
import com.doopp.gauss.api.game.impl.BattleRoyaleGame;
import com.doopp.gauss.api.game.impl.GuessDrawGame;
import com.doopp.gauss.api.game.impl.WerewolfGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RoomSocketHandler extends AbstractWebSocketHandler {

    // logger
    private final static Logger logger = LoggerFactory.getLogger(RoomSocketHandler.class);

    // socket`s session
    private static final Map<Long, WebSocketSession> sockets = new HashMap<>();

    // room`s session
    private static final Map<Integer, RoomEntity> rooms = new HashMap<>();

    // room id
    private static int lastRoomId = 54612;

    // Werewolf , BattleRoyale and GuessDraw
    @Autowired
    private WerewolfGame werewolfGame;

    @Autowired
    private BattleRoyaleGame battleRoyaleGame;

    @Autowired
    private GuessDrawGame guessDrawGame;

    /*
     * 获取房间列表
     */
    public Map<Integer, RoomEntity> getRooms() {
        return rooms;
    }

    // 房间内公共频道说话
    private void roomPublicTalk(RoomEntity sessionRoom, JSONObject messageObject) throws IOException {
        // to json
        TextMessage message = new TextMessage(messageObject.toJSONString());
        // send to front user
        for(UserEntity frontUser : sessionRoom.getFrontUsers().values()) {
            sockets.get(frontUser.getId()).sendMessage(message);
        }
        // send to watch user
        for(UserEntity watchUser : sessionRoom.getWatchUsers().values()) {
            sockets.get(watchUser.getId()).sendMessage(message);
        }
        // send to owner user
        UserEntity owner = sessionRoom.getOwner();
        sockets.get(owner.getId()).sendMessage(message);
    }

    // 房间内游戏频道说话
    private void roomGameTalk(RoomEntity sessionRoom, TextMessage message) throws IOException {
        // send to game user
        for(UserEntity gameUser : sessionRoom.getGameUsers().values()) {
            sockets.get(gameUser.getId()).sendMessage(message);
        }
    }

    // text message
    @Override
    protected void handleTextMessage(WebSocketSession socketSession, TextMessage message) throws Exception {

        // 在哪个房间
        RoomEntity sessionRoom = this.getSessionRoom(socketSession);
        // 是哪个用户
        UserEntity sessionUser = this.getSessionUser(socketSession);
        // 校验消息
        JSONObject messageObject = JSONObject.parseObject(message.getPayload());
        // get action
        String action = messageObject.getString("action");

        // 如果 Action 异常
        if (action==null) {
            logger.info(" >>> no action in message ");
            // socketSession.close();
        }

        // 如果没有进入房间
        else if (sessionRoom==null) {
            this.openRoom(socketSession, sessionUser, messageObject);
        }

        // 如果在房间内，并且 action 正常
        else {
            // 是否有参加活动
            UserEntity joinGameMe = sessionRoom.getGameUsers().get(sessionUser.getId());
            // 活动状态
            RoomEntity.GameStatus gameStatus = sessionRoom.getGameStatus();

            // 如果活动正在进行，并且用户加入了游戏
            if (gameStatus.equals(RoomEntity.GameStatus.Playing) && joinGameMe!=null) {
                // 狼人杀
                if (sessionRoom.getGameType().equals(RoomEntity.GameTypes.WereWolf)) {
                    werewolfGame.handleTextMessage(socketSession, sessionRoom, sessionUser, messageObject);
                }
                // 大逃杀
                else if (sessionRoom.getGameType().equals(RoomEntity.GameTypes.BattleRoyale)) {
                    battleRoyaleGame.handleTextMessage(socketSession, sessionRoom, sessionUser, messageObject);
                }
                // 你画我猜
                else if (sessionRoom.getGameType().equals(RoomEntity.GameTypes.GuessDraw)) {
                    guessDrawGame.handleTextMessage(socketSession, sessionRoom, sessionUser, messageObject);
                }
            }

            // 房主组局
            else if (action.equals("callPlay")) {
                // 房主在房间 的 非游戏时间才能征集游戏玩家
                if (!gameStatus.equals(RoomEntity.GameStatus.Playing) && sessionUser.getId().equals(sessionRoom.getOwner().getId())) {
                    // set room game type
                    switch(messageObject.getString("gameType")) {
                        case "WereWolf" :
                            sessionRoom.setGameType(RoomEntity.GameTypes.WereWolf);
                            break;
                        case "BattleRoyale" :
                            sessionRoom.setGameType(RoomEntity.GameTypes.BattleRoyale);
                            break;
                        case "GuessDraw" :
                            sessionRoom.setGameType(RoomEntity.GameTypes.GuessDraw);
                            break;
                        default:
                            return;
                    }
                    // set game status
                    sessionRoom.setGameStatus(RoomEntity.GameStatus.Calling);
                    // send message
                    this.roomPublicTalk(sessionRoom, messageObject);
                }
            }

            // 玩家参与
            else if (action.equals("joinGame")) {
                // 召集阶段，玩家才能申请参与
                if (gameStatus.equals(RoomEntity.GameStatus.Calling) && sessionRoom.playerNumber()<=12) {
                    // join game
                    sessionRoom.joinGame(sessionUser);
                    // send message
                    this.roomPublicTalk(sessionRoom, messageObject);
                }

                // 人够了就开始游戏
                if (sessionRoom.playerNumber()>=12) {
                    // 提示游戏参与者开始游戏
                    TextMessage textMessage = new TextMessage("{action:\"gameStart\", gameType:\"" + sessionRoom.getGameType() + "\"}");
                    this.roomGameTalk(sessionRoom, textMessage);
                    sessionRoom.setGameStatus(RoomEntity.GameStatus.Playing);
                }
            }

            // 玩家退出组局
            else if (action.equals("leaveGame")) {
                if (gameStatus.equals(RoomEntity.GameStatus.Calling)) {
                    sessionRoom.leaveGame(sessionUser);
                }
            }

            // 没有活动，或这个用户没有加入游戏
            else if (action.equals("publicTalk")) {
                this.roomPublicTalk(sessionRoom, messageObject);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socketSession, CloseStatus status) throws Exception {
        // 关闭连接时，离开房间
        this.leaveRoom(socketSession);
    }

    // 获取房间
    private RoomEntity getSessionRoom(WebSocketSession socketSession) {
        int roomId = (int) socketSession.getAttributes().get("sessionRoomId");
        return roomId==0 ? null : rooms.get(roomId);
    }

    // 获取用户
    private UserEntity getSessionUser(WebSocketSession socketSession) {
        return (UserEntity) socketSession.getAttributes().get("sessionUser");
    }

    // 开房
    private void openRoom(WebSocketSession socketSession, UserEntity sessionUser, JSONObject messageObject) throws Exception {
        // get action
        String action = messageObject.getString("action");
        // 创建房间
        if (action.equals("createRoom")) {
            // 房间名
            String roomName = messageObject.getString("roomName");
            if (roomName!=null) {
                this.createRoom(sessionUser, roomName, socketSession);
                return;
            }
        }
        // 加入到房间
        else if (action.equals("joinRoom")) {
            // 房间 id
            Integer joinRoomId = messageObject.getInteger("roomId");
            if (joinRoomId!=null) {
                this.joinRoom(sessionUser, joinRoomId, socketSession);
                return;
            }
        }
        socketSession.close();
    }

    // 离开房间
    private void leaveRoom(WebSocketSession socketSession) {
        RoomEntity sessionRoom = this.getSessionRoom(socketSession);
        UserEntity sessionUser = this.getSessionUser(socketSession);
        if (sessionRoom!=null) {
            sessionRoom.userLeave(sessionUser);
            if (sessionRoom.getWatchUsers().size()==0 && sessionRoom.getFrontUsers().size()==0 && sessionRoom.getOwner()==null) {
                rooms.remove(sessionRoom.getId());
            }
        }
        sockets.remove(sessionUser.getId());
    }

    // 房主创建房间
    private void createRoom(UserEntity owner, String roomName, WebSocketSession socketSession) {
        RoomEntity roomSession = new RoomEntity();
        synchronized ("createChatRoom") {
            roomSession.setId(++lastRoomId);
        }
        roomSession.setName(roomName);
        roomSession.setOwner(owner);
        // 保存在房间列表
        rooms.put(roomSession.getId(), roomSession);
        // cache 在 socket 列表
        sockets.put(owner.getId(), socketSession);
        // cache socket in room
        socketSession.getAttributes().put("roomId", roomSession.getId());
    }

    // 用户加入房间，普通状态
    private void joinRoom(UserEntity watchUser, int roomId, WebSocketSession socketSession) {
        RoomEntity roomSession = rooms.get(roomId);
        roomSession.joinWatch(watchUser);
        // cache 在 socket 列表
        sockets.put(watchUser.getId(), socketSession);
        // cache socket in room
        socketSession.getAttributes().put("roomId", roomSession.getId());
    }
}
