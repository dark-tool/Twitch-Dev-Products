public class MyEventSub extends TwitchEventSub{

    public MyEventSub() {
        super(50);
    }

    @Override
    public void onWelcomeMessage() {
        // System.out.println("Connected to Twitch WebSocket");

        // Example data from twitch:
        // subscribe("channel.channel_points_custom_reward_redemption.add", "0123456", "2gbdx6oar67tqtcmt49t3wpcgycthx", "wbmytr93xzw8zbg0p1izqyzzc5mbiz");
    }

    @Override
    public void onNotificationMessage(String message) {
        // System.out.println("Subscription Notification");
    }
}
