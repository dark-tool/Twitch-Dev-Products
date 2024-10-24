import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public final class TwitchAPI {
    private TwitchAPI() {}

    public static HttpResponse<String> validateOuath(String oauthToken) {
        HttpResponse<String> response = null;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/validate"))
                .header("Authorization", "OAuth " + oauthToken)
                .build();
        HttpClient client = HttpClient.newHttpClient();
        try {
            response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) { e.printStackTrace(); }
        return response;
    }

    /**
     * Uses Twitch's "Get Users" API to get the data of a specific channel.
     * <li>See https://dev.twitch.tv/docs/api/reference/#get-users
     */
    public static HttpResponse<String> getUserData(String channelName, String clientId, String oauthToken) {
        return getConnectionResponse("https://api.twitch.tv/helix/users?login=" + channelName, clientId, oauthToken);
    }

    /**
     * Uses Twitch's "Get Streams" API to get the data of a current live stream.
     * <li>See https://dev.twitch.tv/docs/api/reference/#get-streams
     */
    public static HttpResponse<String> getLiveStreamData(String channelName, String clientId, String oauthToken) {
        return getConnectionResponse("https://api.twitch.tv/helix/streams?user_login=" + channelName, clientId, oauthToken);
    }

    /**
     * Uses Twitch's "Get Videos" API to get the data of a specific VOD.
     * <li>See https://dev.twitch.tv/docs/api/reference/#get-videos
     */
    public static HttpResponse<String> getVODData(String vodID, String clientId, String oauthToken) {
        return getConnectionResponse("https://api.twitch.tv/helix/videos?id=" + vodID, clientId, oauthToken);
    }

    private static HttpResponse<String> getConnectionResponse(String url, String clientId, String oauthToken) {
        HttpResponse<String> response = null;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + oauthToken)
                .header("Client-Id", clientId)
                .build();
        HttpClient client = HttpClient.newHttpClient();
        try {
            response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) { e.printStackTrace(); }
        return response;
    }
}