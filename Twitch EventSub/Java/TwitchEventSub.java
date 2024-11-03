import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket.Listener;
import java.util.concurrent.CompletionStage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * This class creates a Websocket connection to the Twitch EventSub Websocket. <h4>This class makes use of googles gson library to parse json data.</h4> 
 * <li> Twitch EventSub WebSockets docs: https://dev.twitch.tv/docs/eventsub/handling-websocket-events/
 */
public abstract class TwitchEventSub {
    private String session_id;
    private WebSocket webSocket;
    private boolean wasReconnected = false;
    private boolean isClosed = false;
    
    // this variable is only true during the time span where the first websocket recived a reconnect message and the new websocket recived the welcome message.
    private boolean reconnectingNewWebsocket = false;

    public TwitchEventSub() {
        this(10);
    }

    public TwitchEventSub(int keepalive_timeout_seconds) {
        if(keepalive_timeout_seconds < 10)
            keepalive_timeout_seconds = 10;
        if(keepalive_timeout_seconds > 600)
            keepalive_timeout_seconds = 600;

        this.webSocket = HttpClient.newHttpClient()
                                    .newWebSocketBuilder()
                                    .buildAsync(URI.create("wss://eventsub.wss.twitch.tv/ws?keepalive_timeout_seconds="+keepalive_timeout_seconds), new EventSubListener()).join();
    }

    class EventSubListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("[+OK] WebSocket is open");
            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            JsonObject object = JsonParser.parseString(data.toString()).getAsJsonObject();
            String message_type = object.get("metadata").getAsJsonObject().get("message_type").getAsString();

            switch (message_type) {
                case "session_welcome":
                    session_id = object.get("payload").getAsJsonObject().get("session").getAsJsonObject().get("id").getAsString();
                    if(!wasReconnected) {
                        onWelcomeMessage();
                    } else {
                        TwitchEventSub.this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
                        TwitchEventSub.this.webSocket = webSocket;
                        reconnectingNewWebsocket = false;
                        System.out.println("[+OK] reconnected to new websocket");
                    }
                break;

                case "session_keepalive":
                    System.out.println("[+OK] connected " + object.get("metadata").getAsJsonObject().get("message_timestamp").getAsString().replace("T", " ").substring(0, 19));
                break;

                case "notification":
                    onNotificationMessage(data.toString());
                break;

                case "session_reconnect":
                    reconnectingNewWebsocket = true;
                    System.out.println("[NOTICE] reconnect message recived. Reconnection Websocket...");
                    final String reconnect_url = object.get("payload").getAsJsonObject().get("session").getAsJsonObject().get("reconnect_url").getAsString();
                    TwitchEventSub.this.wasReconnected = true;
                    HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(reconnect_url), new EventSubListener()).join();
                break;

                case "revocation":
                    System.out.println("[-ERR] Revocation Server Response: " + data.toString());
                    close();
                break;
            
                default:
                    System.err.println("[NOTICE] unrecognised message_type: " + data.toString());
                    close();
                break;
            }

            return Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if(statusCode == WebSocket.NORMAL_CLOSURE)
                System.out.println("[NOTICE] WebSocket is closed with statusCode: " + statusCode + " Reason: " + reason);
            else
                System.err.println("[NOTICE] WebSocket is closed with statusCode: " + statusCode + " Reason: " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[-ERR] Error occured: " + error.getMessage());
            error.printStackTrace();
        }
    }

    /**
     * This method should be used to close the connection.
     */
    public final void close() {
        if(isClosed)
            return;

        isClosed = true;
        while (reconnectingNewWebsocket) {
            try { Thread.sleep(1); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        if (!webSocket.isOutputClosed())
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
    }

    /**
     * This method should be used to subscribe to a topic that the websocket should listen to.
     * <li> Twitch docs: https://dev.twitch.tv/docs/api/reference/#create-eventsub-subscription
     * <li> Example: 
     * <pre> {@code
     * subribe("channel.channel_points_custom_reward_redemption.add", "0123456", "2gbdx6oar67tqtcmt49t3wpcgycthx", "wbmytr93xzw8zbg0p1izqyzzc5mbiz");
     * }</pre>
     */
    public final void subscribe(String event, String brodcaster_id, String oauthToken, String clientId) {
        if(isClosed)
            return;
        
        JsonObject object = new JsonObject();
        object.addProperty("type", event);
        object.addProperty("version", "1");

        JsonObject condition = new JsonObject();
        condition.addProperty("broadcaster_user_id", brodcaster_id);
        object.add("condition", condition);

        JsonObject transport = new JsonObject();
        transport.addProperty("method", "websocket");
        transport.addProperty("session_id", session_id);
        object.add("transport", transport);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twitch.tv/helix/eventsub/subscriptions"))
                .POST(BodyPublishers.ofString(object.toString()))
                .header("Authorization", "Bearer " + oauthToken)
                .header("Client-Id", clientId)
                .header("Content-Type", "application/json")
                .build();

        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[+OK] Subscription was send successfully");
            } else {
                System.err.println("[-ERR] Subcribing failed. Server response: " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will only be called once, when the websocket succefully connected to the Twitch WebSocket.
     * <li> It will not be called when reconnecting to a new WebSocket.
     * <li> It is recommeded to use this methode to subscribe to the Subscription Types provided by Twitch.
     */
    public abstract void onWelcomeMessage();

    /**
     * This method will be called with the full response from the server upon reciving a notification from a subscription.
     */
    public abstract void onNotificationMessage(String message);
}
