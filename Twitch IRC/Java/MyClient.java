public class MyClient extends TwitchIRC{
    public MyClient(String oauthToken, String botName) {
        super(oauthToken, botName);
    }

    @Override
    public void onChatMessage(String channel, String username, String message) {
        String command = getCommand(message);
        switch (command.toLowerCase()) {
            // case "!test" : 
            //     sendChatMessage(channel, "Test");
            //     break;
            // case "!quit" :
            //     if(username.equals("<USERNAME>"))
            //         close();
            //     break;
        }
    }

    @Override
    public void onSuccessfulJoin(String channel) {
        // sendChatMessage(channel, "Bot joined");
    }

    @Override
    public void onSuccessfulAuthorisation() {
        // joinChannel("<CHANNEL>");
    }

    @Override
    public void recivedMessage(String response) {
        // System.out.println("[Twitch] " + response);
    }

    public String getCommand(String str) {
        int spaceIndex = str.indexOf(' ');
        if(spaceIndex > 0)
            return str.substring(0, spaceIndex);
        else
            return str;
    }

    public static void main(String[] args) {
        // MyClient client = new MyClient("<OAUTH TOKEN>", "<BOT CHANNEL NAME>");
    }
}
