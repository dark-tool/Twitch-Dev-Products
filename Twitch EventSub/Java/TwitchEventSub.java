import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket.Listener;
import java.time.LocalTime;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class creates a WebSocket connection to the Twitch EventSub WebSocket.
 * <li> Twitch EventSub WebSocket docs: https://dev.twitch.tv/docs/eventsub/handling-websocket-events/
 */
public abstract class TwitchEventSub {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String session_id;
    private WebSocket webSocket;
    private final Object connectionClosedLock = new Object();
    
    // this variable is only true during the time span where the first websocket received a reconnect message and the new websocket received the welcome message.
    private boolean reconnectingNewWebsocket = false;

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED_BOLD = "\033[1;31m";
    public static final String ANSI_GREEN_BOLD = "\033[1;32m";
    public static final String ANSI_BLUE_BOLD = "\033[1;34m";
    public static final String ANSI_WHITE_UNDERLINED = "\033[4;37m";

    public TwitchEventSub() {
        URI uri = URI.create("wss://eventsub.wss.twitch.tv/ws");
        this.webSocket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, new EventSubListener(false)).join();
    }

    public TwitchEventSub(int keepalive_seconds) {
        keepalive_seconds = keepalive_seconds < 10 ? 10 : keepalive_seconds > 600 ? 600 : keepalive_seconds;
        URI uri = URI.create("wss://eventsub.wss.twitch.tv/ws?keepalive_timeout_seconds=" + keepalive_seconds);
        this.webSocket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, new EventSubListener(false)).join();
    }

    public void close() {
        while (reconnectingNewWebsocket) {
            try { Thread.sleep(1); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        if(!webSocket.isOutputClosed())
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

        executorService.shutdownNow();
        
        synchronized(connectionClosedLock) {
            connectionClosedLock.notify();
        }
    }

    /**
     * Sets the thread that called this method to sleep. It will return when the connection to the Twitch WebSocket has been closed.
     * <li>It will not return when reconnecting the WebSocket and the old connection was closed.
     */
    public void join() {
        synchronized(connectionClosedLock) {
            try {
                connectionClosedLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method should be used to subscribe to a topic that the WebSocket should listen to.
     * <li> Twitch docs: https://dev.twitch.tv/docs/api/reference/#create-eventsub-subscription
     * <li> Example: 
     * <pre> {@code
     * Condition condition = newCondition().add("broadcaster_user_id", "0123456");
     * subribe("channel.channel_points_custom_reward_redemption.add", "1", condition, "2gbdx6oar67tqtcmt49t3wpcgycthx", "wbmytr93xzw8zbg0p1izqyzzc5mbiz");
     * }</pre>
     */
    public void subscribe(String event, String version, Condition condition, String acces_token, String client_id) {
        String pay = String.format("""
                                    {
                                        "type": "%s",
                                        "version": "%s",
                                        "condition": {
                                            %s
                                        },
                                        "transport": {
                                            "method": "websocket",
                                            "session_id": "%s"
                                        }
                                    }
                                   """, event, version, condition.toString(), session_id);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twitch.tv/helix/eventsub/subscriptions"))
                .POST(BodyPublishers.ofString(pay))
                .header("Authorization", "Bearer " + acces_token)
                .header("Client-Id", client_id)
                .header("Content-Type", "application/json")
                .build();

        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                event(Event.TWITCH_SUBSCRIPTION_SUCCESS, String.format("%s[OK %d]%s Subscription was send successfully %s", ANSI_GREEN_BOLD, response.statusCode(), ANSI_RESET, event), null);
            } else {
                event(Event.TWITCH_SUBSCRIPTION_FAIL, String.format("%s[ERROR %d]%s Subscription failed %s. Server response: %s", ANSI_RED_BOLD, response.statusCode(), ANSI_RESET, event, response.body()), null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an empty Condition. Use{@code add(String, String)}to append data to it.
     * <li><b>Example:</b>{@code Condition c = newCondition().add("broadcaster_user_id", "123456");}
     */
    public static Condition newCondition() {
        return new Condition();
    }

    /**
     * This method will only be called once, when the WebSocket succefully connected to the Twitch WebSocket.
     * <li> It will not be called when reconnecting to a new WebSocket.
     * <li> <b>Note:</b> It is recommeded to use this methode to subscribe to the Subscription Types provided by Twitch.
     */
    public abstract void onWelcomeMessage();

    /**
     * This method will be called with the full response from the server upon receiving a notification from a subscription.
     * <li>To prevent the websocket from waiting on long operations, a single thread executor is used to call this method.
     */
    public abstract void onNotificationMessage(String message);

    /**
     * This method will be called if an event occured that is listen in{@code TwitchEventSub.Event}. Additional information regarding the event is provided in the{@code infolog}parameter.
     * <li>Unless the event is{@code WEBSOCKET_ERROR}the{@code error}parameter will always be{@code null}.
     * <li>Feel free to override this method in your sub-class.
     */
    public void event(Event event, String infolog, Throwable error) {
        System.out.println(infolog);
        if(error != null)
            error.printStackTrace();
    }

    enum Event {
        WEBSOCKET_OPEN,
        WEBSOCKET_CLOSED,
        WEBSOCKET_ERROR,

        TWITCH_SUBSCRIPTION_SUCCESS,   //---------- twitch specific events ---------------
        TWITCH_SUBSCRIPTION_FAIL,      // (Welcome and Notification have their own method)
        TWITCH_WEBSOCKET_RECONNECTING, // 
        TWITCH_WEBSOCKET_RECONNECTED,  // 
        TWITCH_WEBSOCKET_KEEPALIVE,    // 
        TWITCH_WEBSOCKET_REVOCATION    //-------------------------------------------------
    }

    static class Condition {
        private final StringBuilder sb = new StringBuilder();

        /**
         * Returns the same Condition object for concatination.
         */
        public final Condition add(String name, String value) {
            if(!sb.isEmpty())
                sb.append(',');

            sb.append(String.format("\"%s\": \"%s\"", name, value));
            return this;
        }

        public String toString() {
            return sb.toString();
        }
    }

    class EventSubListener implements WebSocket.Listener {
        private boolean isReconnected;

        public EventSubListener(boolean reconnected) {
            this.isReconnected = reconnected;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            event(Event.WEBSOCKET_OPEN, String.format("%s[OK]%s WebSocket is open", ANSI_GREEN_BOLD, ANSI_RESET), null);
            Listener.super.onOpen(webSocket);
        }
        
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String stringData = data.toString();
            String message_type = getMessageType(stringData);
            
            switch (message_type) {
                case "session_welcome":
                    session_id = getSessionId(stringData);
                    if(!isReconnected) {
                        onWelcomeMessage();
                    } else {
                        TwitchEventSub.this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");
                        TwitchEventSub.this.webSocket = webSocket;
                        reconnectingNewWebsocket = false;
                        event(Event.TWITCH_WEBSOCKET_RECONNECTED, String.format("%s[OK]%s WebSocket reconnected successfully", ANSI_GREEN_BOLD, ANSI_RESET), null);
                    }
                break;
                    
                case "session_keepalive":
                    event(Event.TWITCH_WEBSOCKET_KEEPALIVE, ANSI_GREEN_BOLD + "[OK]" + ANSI_RESET + " Connected " + getMessageTimestamp(stringData).replace("T", " ").substring(0, 19), null);
                break;
                    
                case "notification":
                    executorService.submit(() -> {
                        onNotificationMessage(stringData);
                    });
                break;
                    
                case "session_reconnect":
                    reconnectingNewWebsocket = true;
                    event(Event.TWITCH_WEBSOCKET_RECONNECTING, String.format("%s[NOTICE]%s Reconnect message received. Reconnecting Websocket...", ANSI_BLUE_BOLD, ANSI_RESET), null);
                    URI reconnect_url = null;
                    try {
                        reconnect_url = new URI(getReconnectURL(stringData).replaceAll("\\\\u0026", "&"));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    
                    HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(reconnect_url, new EventSubListener(true));
                break;
                    
                case "revocation":
                    event(Event.TWITCH_WEBSOCKET_REVOCATION, String.format("%s[ERROR]%s Revocation Server Response: %s", ANSI_RED_BOLD, ANSI_RESET, stringData), null);
                    close();
                break;
            }
            return Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if(statusCode != WebSocket.NORMAL_CLOSURE) {
                event(Event.WEBSOCKET_CLOSED, String.format("%s[CONNECTION ERROR]%s %s WebSocket is closed with statusCode: %d Reason: %s", ANSI_RED_BOLD, ANSI_RESET, LocalTime.now().toString(), statusCode, reason), null);
                close();
            }
            else {
                event(Event.WEBSOCKET_CLOSED, String.format("%s[NOTICE]%s %s WebSocket is closed with statusCode: %d reason: %s", ANSI_BLUE_BOLD, ANSI_RESET, LocalTime.now().toString(), statusCode, reason), null);
            }
            return null;
        }
        
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            event(Event.WEBSOCKET_ERROR, String.format("%s[WEBSOCKET ERROR]%s %s Error occured : %s", ANSI_RED_BOLD, ANSI_RESET, LocalTime.now().toString(), error.getMessage()), error);
            close();
            synchronized(connectionClosedLock) {
                connectionClosedLock.notify();
            }
        }

        
        private String getMessageType(String data) {
            return getJsonValue(data, "\"message_type\":");
        }
        
        private String getSessionId(String data) {
            return getJsonValue(data, "\"id\":");
        }

        private String getReconnectURL(String data) {
            return getJsonValue(data, "\"reconnect_url\":");
        }
        
        private String getMessageTimestamp(String data) {
            return getJsonValue(data, "\"message_timestamp\":");
        }

        // only works if both key and value are strings
        private String getJsonValue(String data, String key) {
            int index = data.indexOf(key);
            int startindex = data.indexOf("\"", index + key.length());
            int endindex = data.indexOf("\"", startindex+1);
            return data.substring(startindex+1, endindex);
        }
    }
}
