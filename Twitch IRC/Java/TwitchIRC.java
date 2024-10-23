import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;

public abstract class TwitchIRC {
    private final String ip = "irc.chat.twitch.tv";
    private final int port = 6667;
    private MessageHandler messageHandler;
    private LinkedList<String> connectedChannels = new LinkedList<>();
    private boolean isAuthorised = false;

    private class MessageHandler extends Thread {
        private SocketConnection socketConnection;
        private boolean isConnected;
        
        private class SocketConnection {
            private Socket socket;
            private BufferedReader reader;
            private PrintWriter writer;

            public SocketConnection(String ip, int port) {
                try {
                    socket = new Socket(ip, port);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer = new PrintWriter(socket.getOutputStream(), true);
                } catch (IOException e) {
                    reader = null;
                    writer = null;
                    socket = null;
                    e.printStackTrace();
                }
            }

            public String recive() {
                if(reader != null)
                    try {
                        return reader.readLine();
                    } catch (IOException e) { e.printStackTrace(); }
                return null;
            }

            public void send(String message) {
                if(writer != null)
                    writer.println(message);
            }

            public void close() {
                try {
                    if(writer != null)
                        writer.close();
                    if(reader != null)
                        reader.close();
                    if(socket != null)
                        socket.close();
                } catch (IOException e) { e.printStackTrace(); }
            }
        }

        public MessageHandler(String ip, int port) {
            socketConnection = new SocketConnection(ip, port);
            if(socketConnection.socket != null)
                isConnected = true;

            // start new thread to listen on incoming messages from twitch
            start();
        }

        @Override
        public void run() {
            String serverMessage;
            while (isConnected) {
                serverMessage = socketConnection.recive();
                if(serverMessage != null) {
                    recivedMessage(serverMessage);

                    String[] sM = serverMessage.split(" ", 4);

                    if(sM[0].equals("PING")) 
                    {
                        send("PONG " + sM[1]);
                    }
                    else if(sM[1].equals("001"))
                    {
                        isAuthorised = true;
                        onSuccessfulAuthorisation();
                    }
                    else if(sM[1].equals("366"))
                    {
                        String channel = sM[3].substring(1, sM[3].indexOf(' '));
                        connectedChannels.add(channel);
                        onSuccessfulJoin(channel);
                    }
                    else if(sM[1].equals("PRIVMSG"))
                    {
                        String user = sM[0].substring(1, sM[0].indexOf("!"));
                        String channel = sM[2].substring(1);
                        String msg = sM[3].substring(1);
                        onChatMessage(channel, user, msg);
                    }
                } else {
                    close();
                }
            }
        }

        public synchronized void send(String message) {
            if(isConnected)
                socketConnection.send(message);
        }

        public synchronized void close() {
            if(isConnected) {
                isConnected = false;
                isAuthorised = false;
                socketConnection.close();
            }
        }
    }

    public TwitchIRC(String oauthToken, String botName) {
        // connect to twitch irc
        messageHandler = new MessageHandler(ip, port);

        // authorize to twitch irc
        if(isConnected()) {
            messageHandler.send("PASS oauth:" + oauthToken);
            messageHandler.send("NICK " + botName);
        }
    }

    /**
     * Closes the connection to the server.
     */
    public final void close() {
        quitAllChannels();
        messageHandler.close();
    }

    /**
     * This method will send the passed String to the server, without adding anything extra. It should not be called by the user, unless they have knowledge about the Twitch IRC.
     */
    public final void send(String message) {
        // System.out.println("[USER] " + message);
        messageHandler.send(message);
    }

    /**
     * This method should be used to join a channel.
     */
    public final void joinChannel(String channel) {
        if(isAuthorised && !connectedChannels.contains(channel))
            send("JOIN #" + channel);
    }

    /**
     * This method should be used to leave a channel.
     */
    public final void quitChannel(String channel) {
        if(isAuthorised && connectedChannels.contains(channel)) {
            connectedChannels.remove(channel);
            send("PART #" + channel);
        }
    }

    public final void quitAllChannels() {
        if(isAuthorised)
            connectedChannels.forEach(channel -> quitChannel(channel));
    }

    /**
     * This method should be used to send a message to a channel.
     */
    public final void sendChatMessage(String channel, String message) {
        if(isAuthorised && connectedChannels.contains(channel))
            send("PRIVMSG #" + channel + " :" + message);
    }

    public final void sendChatMessageToAllChannels(String message) {
        if(isAuthorised)
            connectedChannels.forEach(channel -> sendChatMessage(channel, message));
    }

    /**
     * Returns true if still connected to the server.
     */
    public final boolean isConnected() {
        return messageHandler.isConnected;
    }

    /**
     * This method will be called when a user sends a message to a channel that the bot is connected to.
     */
    public abstract void onChatMessage(String channel, String username, String message);
    
    /**
     * This method will be called when the bot successfully joined a channel.
     */
    public abstract void onSuccessfulJoin(String channel);
    
    /**
     * This method will only be called once when the bot was able to successfully authorise to the server.
     */
    public abstract void onSuccessfulAuthorisation();

    /**
     * This method will always be called with the full response message from the server. Good for console output.
     */
    public abstract void recivedMessage(String response);
}