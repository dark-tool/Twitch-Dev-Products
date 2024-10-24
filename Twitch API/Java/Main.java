import java.net.http.HttpResponse;

public class Main {
    public static void main(String[] args) {
        String channelName = "<CHANNEL_NAME>";
        String clientID = "<CLIENT_ID>";
        String oauthToken = "<OAUTH_TOKEN";

        HttpResponse<String> response = TwitchAPI.validateOuath(oauthToken);
        if (response != null)
            System.out.println(response.body());

        response = TwitchAPI.getUserData(channelName, clientID, oauthToken);
        if (response != null)
            System.out.println(response.body());
    }
}
