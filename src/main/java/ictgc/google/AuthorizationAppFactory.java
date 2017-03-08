package ictgc.google;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import ictgc.ApplicationProperties;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationAppFactory {

    private static final String REDIRECT_URL_RESPONSE = "<html><head>" +
            "<title>OAuth 2.0 Authentication Token Received</title></head>" +
            "<body>All done, you may now close this window...</body></html>";

    private static final String CALLBACK_PATH = "/google-calendar-auth-callback";

    private final String redirectUrl;
    private final ConcurrentMap<String, VerificationCodeReceiverImpl> receivers = new ConcurrentHashMap<>();

    @Autowired
    public AuthorizationAppFactory(ApplicationProperties config) {
        ApplicationProperties.AuthorizationServer authorizationServerConfig = config.getAuthorizationServer();
        String host = authorizationServerConfig.getHost();
        int port = authorizationServerConfig.getPort();

        this.redirectUrl = "http://" + host + ":" + port + CALLBACK_PATH;

        Server server = new Server(port);
        server.addHandler(new CallbackHandler());
        try {
            server.start();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthorizationApp createAuthorizationApp(AuthorizationCodeFlow flow) {
        String receiverId = UUID.randomUUID().toString();
        VerificationCodeReceiverImpl receiver = new VerificationCodeReceiverImpl();
        receivers.putIfAbsent(receiverId, receiver);
        return new AuthorizationApp(flow, receiverId, receiver);
    }

    private void handleCallback(String state, String code, String error) {
        VerificationCodeReceiverImpl receiver = receivers.get(state);
        if (receiver == null) {
            throw new IllegalStateException("Unknown receiver " + state);
        }
        receiver.setAuthorizationResponse(code, error);
    }

    private class CallbackHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException {

            if (!CALLBACK_PATH.equals(target)) {
                return;
            }

            writeRedirectUrlHtml(response);
            response.flushBuffer();
            ((Request) request).setHandled(true);

            String error = request.getParameter("error");
            String code = request.getParameter("code");
            String state = request.getParameter("state");
            handleCallback(state, code, error);
        }

        private void writeRedirectUrlHtml(HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            PrintWriter doc = response.getWriter();
            doc.println(REDIRECT_URL_RESPONSE);
            doc.flush();
        }
    }

    private class VerificationCodeReceiverImpl implements VerificationCodeReceiver {

        private final Lock lock = new ReentrantLock();
        private final Condition gotAuthorizationResponse = lock.newCondition();
        private String code;
        private String error;

        @Override
        public String getRedirectUri() throws IOException {
            return redirectUrl;
        }

        @Override
        public String waitForCode() throws IOException {
            lock.lock();
            try {
                while (code == null && error == null) {
                    gotAuthorizationResponse.awaitUninterruptibly();
                }

                if (error != null) {
                    throw new IOException("User authorization failed (" + error + ")");
                }

                return code;
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void stop() throws IOException {
            // nothing to do, server is alive for application lifetime
        }

        public void setAuthorizationResponse(String code, String error) {
            lock.lock();
            try {
                this.code = code;
                this.error = error;

                gotAuthorizationResponse.signal();
            }
            finally {
                lock.unlock();
            }
        }
    }

    public static final class AuthorizationApp {

        private final String state;
        private final AuthorizationCodeFlow flow;
        private final VerificationCodeReceiver receiver;

        private AuthorizationApp(AuthorizationCodeFlow flow, String state, VerificationCodeReceiver receiver) {
            this.flow = flow;
            this.state = state;
            this.receiver = receiver;
        }

        public Credential authorize(String userId) throws IOException {
            Credential credential = flow.loadCredential(userId);
            if (credential != null
                    && (credential.getRefreshToken() != null || credential.getExpiresInSeconds() > 60)) {
                return credential;
            }
            // open in browser
            String redirectUri = receiver.getRedirectUri();
            AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .setState(state);

            System.out.println("Please open the following address in your browser:");
            System.out.println("  " + authorizationUrl);

            String code = receiver.waitForCode();

            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            return flow.createAndStoreCredential(response, userId);
        }

    }
}
