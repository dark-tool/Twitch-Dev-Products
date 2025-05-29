public class MyEventSub extends TwitchEventSub{

    public MyEventSub() {
        super(50); // <--- This is optional. If removed then default is 10, equivalent to super(10);
    }

    @Override
    public void onWelcomeMessage() {
        System.out.println("Welcome message received");

        String event = "channel.channel_points_custom_reward_redemption.add";
        String version = "1";
        Condition condition = newCondition().add("broadcaster_user_id", "0123456");
        String access_token = "<Your access token>";
        String cliend_id = "<Your client id>";

        subscribe(event, version, condition, access_token, cliend_id);
    }

    @Override
    public void onNotificationMessage(String message) {
        System.out.println("Entire message from Twitch: " + message);
    }
}
