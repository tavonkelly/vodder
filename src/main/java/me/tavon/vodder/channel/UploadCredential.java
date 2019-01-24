package me.tavon.vodder.channel;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import me.tavon.vodder.Vodder;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants.AUTHORIZATION_SERVER_URL;
import static com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants.TOKEN_SERVER_URL;

public class UploadCredential {

    private Credential credential;

    private UploadCredential(Credential credential) {
        Objects.requireNonNull(credential, "credential cannot be null");
        this.credential = credential;
    }

    public Credential getCredential() {
        return credential;
    }

    public static UploadCredential getUploadCredential(Channel channel) throws Exception {
        File file = new File(Vodder.CHANNELS_PATH + channel.getChannelId() + "/StoredCredential");

        if (!file.exists() || !file.isFile()) {
            Vodder.LOGGER.info("Requesting YouTube channel to upload VODs for " + channel.getChannelId());
        }

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken
                .authorizationHeaderAccessMethod(),
                new NetHttpTransport(),
                new JacksonFactory(),
                new GenericUrl(TOKEN_SERVER_URL),
                new ClientParametersAuthentication(
                        ChannelAuthConstants.API_KEY, ChannelAuthConstants.API_SECRET),
                ChannelAuthConstants.API_KEY,
                AUTHORIZATION_SERVER_URL).setScopes(Arrays.asList("https://www.googleapis.com/auth/youtube",
                "https://www.googleapis.com/auth/youtubepartner"))
                .setDataStoreFactory(new FileDataStoreFactory(file.getParentFile()))
                .build();
        AbstractPromptReceiver receiver = new AbstractPromptReceiver() {
            @Override
            public String getRedirectUri() {
                return GoogleOAuthConstants.OOB_REDIRECT_URI;
            }
        };
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(channel.getChannelId());

        return new UploadCredential(credential);
    }
}
