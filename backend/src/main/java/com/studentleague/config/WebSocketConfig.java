package com.studentleague.config;

import com.studentleague.security.JwtService;
import com.studentleague.security.UserPrincipal;
import com.studentleague.users.repository.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public WebSocketConfig(JwtService jwtService, UserRepository userRepository, AppProperties appProperties) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(appProperties.cors().allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String auth = accessor.getFirstNativeHeader("Authorization");
                    if (auth == null) {
                        auth = accessor.getFirstNativeHeader("authorization");
                    }
                    String token = null;
                    if (auth != null && auth.startsWith("Bearer ")) {
                        token = auth.substring(7);
                    }
                    if (token == null) {
                        var query = accessor.getNativeHeader("token");
                        if (query != null && !query.isEmpty()) {
                            token = query.getFirst();
                        }
                    }
                    if (token != null) {
                        try {
                            JwtService.ParsedToken parsed = jwtService.parse(token);
                            userRepository.findById(parsed.userId()).ifPresent(user -> {
                                if (user.isEnabled()) {
                                    UserPrincipal principal = UserPrincipal.from(user);
                                    accessor.setUser(new UsernamePasswordAuthenticationToken(
                                            principal, null, principal.getAuthorities()));
                                }
                            });
                        } catch (RuntimeException ignored) {
                            // leave unauthenticated; broker may still allow topic subscribe for public live scores
                        }
                    }
                }
                return message;
            }
        });
    }
}
