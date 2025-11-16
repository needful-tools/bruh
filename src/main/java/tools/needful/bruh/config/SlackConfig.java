package tools.needful.bruh.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {

    @Value("${slack.bot.token}")
    private String botToken;

    @Value("${slack.bot.app-token}")
    private String appToken;

    @Bean
    public App slackApp() {
        return new App();
    }

    @Bean
    public SocketModeApp socketModeApp(App app) throws Exception {
        SocketModeApp socketModeApp = new SocketModeApp(appToken, app);
        socketModeApp.startAsync();
        return socketModeApp;
    }
}
