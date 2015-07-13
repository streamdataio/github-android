package tylerjroach.com.eventsource_android;

import java.io.IOException;

public interface EventSourceHandler {

    /**
     * SSE handler for connection starting
     */
    public void onConnect() throws  Exception;

    /**
     * SSE incoming message handler
     * @param event type of message
     * @param message message JSON content
     * @throws IOException if JSON syntax is not valid
     */
    public void onMessage(String event, MessageEvent message) throws IOException, Exception;

    /**
     * SSE error Handler
     */
    public void onError(Throwable t);

    /**
     * SSE Handler for connection interruption
     */
    public void onClosed(boolean willReconnect);

}

