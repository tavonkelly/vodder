package me.tavon.vodder.stream.chat;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ChatIngest implements Serializable {

    private static final long serialVersionUID = -6977212779076716377L;

    private Map<String, ChatMessage> chatMessages = new HashMap<>();

    public void submitChatMessage(ChatMessage chatMessage) {
        chatMessages.put(chatMessage.getId(), chatMessage);
    }

    public Map<String, ChatMessage> getChatMessages() {
        return chatMessages;
    }

    public List<ChatMessage> getMessagesInRange(long start, long end) {
        List<ChatMessage> messages = new LinkedList<>();

        for (ChatMessage message : chatMessages.values()) {
            if (message.getTimestamp() >= start && message.getTimestamp() < end) {
                messages.add(message);
            }
        }

        return messages;
    }
}
