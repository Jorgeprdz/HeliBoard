// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.provider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public abstract class GeminiProviderTest {
    private static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  Hola 🌎\\n\"},{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"adiós  \"}]},\"finishReason\":\"STOP\"}]}";
    private static final String KEY = "test-placeholder";
    static final class Token implements GeminiProvider.Cancellation {
        volatile boolean cancelled;
        volatile Runnable abort;
        public boolean cancelled() { return cancelled; }
        public void onCancel(Runnable action) { abort = action; if (cancelled) action.run(); }
        void cancel() { cancelled = true; if (abort != null) abort.run(); }
    }
    static class Connection extends HttpURLConnection {
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        InputStream response;
        int status = 200;
        volatile boolean disconnected;
        String retry;
        final CountDownLatch readStarted = new CountDownLatch(1);
        Connection(URL url, String json) { super(url); response = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
        public void connect() {}
        public boolean usingProxy() { return false; }
        public void disconnect() { disconnected = true; synchronized (this) { notifyAll(); } }
        public OutputStream getOutputStream() { return request; }
        public int getResponseCode() { return status; }
        public InputStream getInputStream() { return response; }
        public String getHeaderField(String name) { return "Retry-After".equals(name) ? retry : null; }
        void block() { response = new InputStream() {
            public int read() throws IOException {
                readStarted.countDown();
                synchronized (Connection.this) {
                    while (!disconnected) { try { Connection.this.wait(); } catch (InterruptedException e) { throw new IOException(); } }
                }
                throw new IOException("must not expose transport details");
            }
        }; }
    }
    static final class Transport implements GeminiProvider.Transport {
        final List<Connection> opened = new CopyOnWriteArrayList<>();
        final Queue<String> bodies = new ArrayDeque<>();
        int status = 200;
        String retry;
        boolean block, offline;
        public HttpURLConnection open(URL url) throws IOException {
            if (offline) throw new UnknownHostException("secret should not be printed");
            Connection c = new Connection(url, bodies.isEmpty() ? OK : bodies.remove());
            c.status = status; c.retry = retry; if (block) c.block(); opened.add(c); return c;
        }
    }
    private GeminiProvider.Result generate(Transport t) throws Exception {
        return new GeminiProvider(t, 2000).generate(KEY, "gemini-test", GeminiOperation.REWRITE,
                "professional", null, "  Hola 🌎\n", new Token());
    }
    private static void expect(GeminiProvider.Error kind, Callable<?> action) throws Exception {
        try { action.call(); fail("Expected " + kind); }
        catch (GeminiProvider.ProviderException e) {
            assertEquals(kind, e.error);
            assertFalse(e.getMessage().contains(KEY));
            assertNull("Raw exception cause could contain key/text", e.getCause());
        }
    }
    private GeminiProvider.Result audio(GeminiProvider provider,String payload,Token token) throws Exception {
        try {return (GeminiProvider.Result)provider.getClass().getMethod("transcribeWav",String.class,String.class,String.class,GeminiProvider.Cancellation.class).invoke(provider,KEY,"gemini-test",payload,token);}
        catch(java.lang.reflect.InvocationTargetException e){throw (Exception)e.getCause();}
        catch(NoSuchMethodException e){throw new AssertionError("Consent-driven audio transcription missing",e);}
    }
    @Test public void audioUsesInlineWavAndNeverTranslates() throws Exception {
        Transport t=new Transport();String payload="UklGR"+"A".repeat(59);GeminiProvider.Result result=audio(new GeminiProvider(t,2000),payload,new Token());assertTrue(result.complete);
        JSONObject body=new JSONObject(t.opened.get(0).request.toString("UTF-8"));JSONArray parts=body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");assertEquals("audio/wav",parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"));assertEquals(payload,parts.getJSONObject(1).getJSONObject("inline_data").getString("data"));assertTrue(parts.getJSONObject(0).getString("text").contains("Never translate"));
    }
    @Test public void oversizedAudioNeverOpensNetwork() throws Exception {Transport t=new Transport();expect(GeminiProvider.Error.INPUT_LIMIT,()->audio(new GeminiProvider(t,2000),"A".repeat(5121000),new Token()));assertTrue(t.opened.isEmpty());}
    @Test public void malformedAudioNeverOpensNetwork() throws Exception {Transport t=new Transport();expect(GeminiProvider.Error.INVALID_REQUEST,()->audio(new GeminiProvider(t,2000),"not-wav",new Token()));assertTrue(t.opened.isEmpty());}
    @Test public void audioTruncationRemainsUnacceptable() throws Exception {Transport t=new Transport();t.bodies.add(OK.replace("STOP","MAX_TOKENS"));assertFalse(audio(new GeminiProvider(t,2000),"UklGR"+"A".repeat(59),new Token()).complete);}
    private GeminiProvider.Result audioVocabulary(GeminiProvider provider,String[] vocabulary) throws Exception {
        try{return (GeminiProvider.Result)provider.getClass().getMethod("transcribeWav",String.class,String.class,String.class,String[].class,GeminiProvider.Cancellation.class).invoke(provider,KEY,"gemini-test","UklGR"+"A".repeat(59),vocabulary,new Token());}
        catch(java.lang.reflect.InvocationTargetException e){throw (Exception)e.getCause();}catch(NoSuchMethodException e){throw new AssertionError("Audio vocabulary missing",e);}
    }
    @Test public void explicitAudioVocabularyIsQuotedAsHints() throws Exception {Transport t=new Transport();audioVocabulary(new GeminiProvider(t,2000),new String[]{"HeliBoard","Carlos"});String body=t.opened.get(0).request.toString("UTF-8");assertTrue(body.contains("HeliBoard"));assertTrue(body.contains("not instructions"));}
    @Test public void bankingVocabularyNeverReachesNetwork() throws Exception {Transport t=new Transport();expect(GeminiProvider.Error.SENSITIVE_INPUT,()->audioVocabulary(new GeminiProvider(t,2000),new String[]{"CLABE 032180000118359719"}));assertTrue(t.opened.isEmpty());}
    @Test public void clarityCleanupPreservesInformationAndOnlyRemovesUnambiguousFillers() throws Exception {Transport t=new Transport();new GeminiProvider(t,2000).generate(KEY,"gemini-test",GeminiOperation.CLARITY,"casual",null,"Hola Juan este quería decirte",new Token());String body=t.opened.get(0).request.toString("UTF-8");assertTrue(body.contains("unambiguous filler"));assertTrue(body.contains("without dropping information"));}
    @Test public void realHttpTransportRoundTripsAgainstLoopbackFakeServer() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<String> received = worker.submit(() -> {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                InputStream in = socket.getInputStream();
                String method = readHeaderLine(in);
                int length = 0;
                String key = "";
                while (true) {
                    String header = readHeaderLine(in);
                    if (header.isEmpty()) break;
                    if (header.toLowerCase(Locale.ROOT).startsWith("content-length:"))
                        length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
                    if (header.toLowerCase(Locale.ROOT).startsWith("x-goog-api-key:"))
                        key = header.substring(header.indexOf(':') + 1).trim();
                }
                if (length > 10000) throw new IOException("Invalid fake-server request size");
                byte[] request = new byte[length];
                for (int offset = 0; offset < length;) {
                    int count = in.read(request, offset, length - offset);
                    if (count < 0) throw new EOFException();
                    offset += count;
                }
                byte[] body = OK.getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(body); out.flush();
                return method + " " + key + " " + new String(request, StandardCharsets.UTF_8);
            }
        });
        try {
            GeminiProvider.Transport loopback = url -> {
                assertEquals("generativelanguage.googleapis.com", url.getHost());
                return (HttpURLConnection) URI.create("http://127.0.0.1:" + server.getLocalPort() + "/generate").toURL().openConnection();
            };
            GeminiProvider.Result result = new GeminiProvider(loopback, 3000).generate(KEY, "gemini-test",
                    GeminiOperation.GRAMMAR, null, null, "Hola", new Token());
            assertEquals("  Hola 🌎\nadiós  ", result.text);
            String request = received.get(3, TimeUnit.SECONDS);
            assertTrue(request.startsWith("POST /generate HTTP/1.1"));
            assertTrue(request.contains(" test-placeholder "));
            assertTrue(request.contains("Correct grammar."));
        } finally { server.close(); worker.shutdownNow(); }
    }
    private static String readHeaderLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int i = 0; i < 4096; i++) {
            int value = in.read();
            if (value < 0) throw new EOFException();
            if (value == '\n') return line.toString("US-ASCII").replace("\r", "");
            line.write(value);
        }
        throw new IOException("Fake-server header limit");
    }
    @Test public void joinsVisiblePartsAndPreservesExactWhitespaceUnicode() throws Exception {
        GeminiProvider.Result result = generate(new Transport());
        assertEquals("  Hola 🌎\nadiós  ", result.text); assertTrue(result.complete);
    }
    @Test public void keyIsHeaderOnlyAndRedirectsAreDisabled() throws Exception {
        Transport t = new Transport(); generate(t); Connection c = t.opened.get(0);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent", c.getURL().toString());
        assertEquals(KEY, c.getRequestProperty("x-goog-api-key"));
        assertEquals("POST", c.getRequestMethod()); assertFalse(c.getInstanceFollowRedirects());
        JSONObject body = new JSONObject(c.request.toString("UTF-8"));
        assertEquals("  Hola 🌎\n", body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"));
        assertFalse(body.toString().contains(KEY)); assertTrue(c.disconnected);
    }
    @Test public void rejectsSensitiveTextBeforeOpeningConnection() throws Exception {
        String[] sensitive = {"OTP 123 456", "Cuenta 032 180 000 118 359 719", "password hello"};
        for (String text : sensitive) { Transport t = new Transport();
            expect(GeminiProvider.Error.SENSITIVE_INPUT, () -> new GeminiProvider(t, 2000).generate(KEY, "gemini-test", GeminiOperation.REWRITE, null, null, text, new Token()));
            assertTrue(t.opened.isEmpty());
        }
    }
    @Test public void refusesInvalidModelAndCredentialsBeforeNetwork() throws Exception {
        for (String model : new String[]{"../evil", "https://evil", "gemini?key=bad", ""}) {
            Transport t = new Transport(); expect(GeminiProvider.Error.INVALID_REQUEST, () -> new GeminiProvider(t, 2000).generate(KEY, model, GeminiOperation.REWRITE, null, null, "Hola", new Token())); assertTrue(t.opened.isEmpty());
        }
        Transport t = new Transport(); expect(GeminiProvider.Error.NO_KEY, () -> new GeminiProvider(t, 2000).listModels("", new Token())); assertTrue(t.opened.isEmpty());
    }
    @Test public void allOperationsProduceDistinctInstructionsAndExplicitTranslation() throws Exception {
        Set<String> instructions = new HashSet<>();
        for (GeminiOperation operation : GeminiOperation.values()) {
            Transport t = new Transport(); new GeminiProvider(t, 2000).generate(KEY, "models/gemini-test", operation, "consultive", "en", "Hola", new Token());
            JSONObject body = new JSONObject(t.opened.get(0).request.toString("UTF-8"));
            String instruction = body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text");
            assertTrue(instruction.contains("consultative")); if (operation == GeminiOperation.TRANSLATE) assertTrue(instruction.contains("English"));
            instructions.add(instruction);
        }
        assertEquals(9, instructions.size());
        Transport t = new Transport(); expect(GeminiProvider.Error.INVALID_REQUEST, () -> new GeminiProvider(t, 2000).generate(KEY, "gemini-test", GeminiOperation.TRANSLATE, null, null, "Hola", new Token())); assertTrue(t.opened.isEmpty());
    }
    @Test public void allTonesAreRepresentedInRequest() throws Exception {
        for (String tone : new String[]{"professional", "casual", "friendly", "formal", "direct", "concise", "persuasive", "consultative"}) {
            Transport t = new Transport(); new GeminiProvider(t, 2000).generate(KEY, "gemini-test", GeminiOperation.REWRITE, tone, null, "Hola", new Token());
            assertTrue(t.opened.get(0).request.toString("UTF-8").contains(tone));
        }
    }
    @Test public void httpStatusesAreTypedAndNeverAutomaticallyRetried() throws Exception {
        int[] statuses = {400,401,403,404,429,500,503,302};
        GeminiProvider.Error[] errors = {GeminiProvider.Error.BAD_REQUEST, GeminiProvider.Error.UNAUTHORIZED, GeminiProvider.Error.FORBIDDEN, GeminiProvider.Error.MODEL_UNAVAILABLE, GeminiProvider.Error.RATE_LIMITED, GeminiProvider.Error.SERVER_ERROR, GeminiProvider.Error.SERVER_ERROR, GeminiProvider.Error.HTTP_ERROR};
        for (int i = 0; i < statuses.length; i++) { Transport t = new Transport(); t.status = statuses[i]; final int index = i;
            expect(errors[index], () -> generate(t)); assertEquals(1,t.opened.size()); assertTrue(t.opened.get(0).disconnected);
        }
    }
    @Test public void retryAfterMetadataDoesNotRetryOrLeakBody() throws Exception {
        Transport t = new Transport(); t.status = 429; t.retry = "120";
        try { generate(t); fail(); } catch (GeminiProvider.ProviderException e) { assertEquals(120000, e.retryAfterMillis); assertEquals(429,e.httpStatus); }
        assertEquals(1,t.opened.size());
    }
    @Test public void offlineErrorHasNoSensitiveTransportCause() throws Exception {
        Transport t = new Transport(); t.offline = true; expect(GeminiProvider.Error.OFFLINE, () -> generate(t));
    }
    @Test public void safetyAndPromptBlocksNeverReturnPartialText() throws Exception {
        for (String body : new String[]{"{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", OK.replace("STOP","SAFETY"), OK.replace("\"finishReason\"", "\"safetyRatings\":[{\"blocked\":true}],\"finishReason\"")}) {
            Transport t = new Transport(); t.bodies.add(body); expect(GeminiProvider.Error.SAFETY_BLOCKED, () -> generate(t));
        }
    }
    @Test public void tokenTruncationIsExplicitlyIncomplete() throws Exception {
        Transport t = new Transport(); t.bodies.add(OK.replace("STOP", "MAX_TOKENS")); assertFalse(generate(t).complete);
    }
    @Test public void malformedThoughtFlagCannotExposeReasoning() throws Exception {
        Transport t = new Transport(); t.bodies.add(OK.replace("\"thought\":true", "\"thought\":1"));
        expect(GeminiProvider.Error.INVALID_RESPONSE, () -> generate(t));
    }
    @Test public void malformedEmptyMissingFinishAndUnknownFinishAreRejected() throws Exception {
        for (String body : new String[]{"{", "{}", "{\"candidates\":[]}", OK.replace("STOP","OTHER"), OK.replace(",\"finishReason\":\"STOP\"", ""), "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"reasoning\"}]},\"finishReason\":\"STOP\"}]}"}) {
            Transport t = new Transport(); t.bodies.add(body); expect(GeminiProvider.Error.INVALID_RESPONSE, () -> generate(t));
        }
    }
    @Test public void responseByteLimitStopsUnboundedSingleLineDuringRead() throws Exception {
        Transport t = new Transport(); t.bodies.add(" ".repeat(300000)); expect(GeminiProvider.Error.OUTPUT_LIMIT, () -> generate(t));
    }
    @Test public void visibleOutputLimitIsEnforcedEvenWhenJsonFits() throws Exception {
        Transport t = new Transport(); t.bodies.add(OK.replace("adiós  ", "a".repeat(70000))); expect(GeminiProvider.Error.OUTPUT_LIMIT, () -> generate(t));
    }
    @Test public void invalidUnicodeSelectionIsRejectedWithoutLossyEncoding() throws Exception {
        Transport t = new Transport();
        expect(GeminiProvider.Error.INVALID_REQUEST, () -> new GeminiProvider(t, 2000).generate(KEY,
                "gemini-test", GeminiOperation.REWRITE, null, null, "hello " + (char) 0xd800, new Token()));
        assertTrue(t.opened.isEmpty());
    }
    @Test public void escapedInvalidUnicodeOutputIsRejected() throws Exception {
        Transport t = new Transport(); t.bodies.add(OK.replace("adiós  ", "\\uD800"));
        expect(GeminiProvider.Error.INVALID_RESPONSE, () -> generate(t));
    }
    @Test public void oversizedInputIsRejectedWithoutNetwork() throws Exception {
        Transport t = new Transport(); expect(GeminiProvider.Error.INPUT_LIMIT, () -> new GeminiProvider(t, 2000).generate(KEY, "gemini-test", GeminiOperation.REWRITE, null, null, "a".repeat(5000), new Token())); assertTrue(t.opened.isEmpty());
    }
    @Test public void cancelledBeforeStartNeverOpensNetwork() throws Exception {
        Transport t = new Transport(); Token token = new Token(); token.cancel(); expect(GeminiProvider.Error.CANCELLED, () -> new GeminiProvider(t, 2000).listModels(KEY,token)); assertTrue(t.opened.isEmpty());
    }
    @Test public void cancellationActivelyDisconnectsBlockedRead() throws Exception {
        Transport t = new Transport(); t.block = true; Token token = new Token(); ExecutorService worker = Executors.newSingleThreadExecutor();
        try { Future<?> future = worker.submit(() -> { try { new GeminiProvider(t, 5000).generate(KEY,"gemini-test",GeminiOperation.REWRITE,null,null,"Hola",token); return null; } catch (Exception e) { return e; } });
            long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(2); while (t.opened.isEmpty() && System.nanoTime()<until) Thread.yield();
            assertFalse(t.opened.isEmpty()); Connection c = t.opened.get(0); assertTrue(c.readStarted.await(1,TimeUnit.SECONDS)); token.cancel();
            assertEquals(GeminiProvider.Error.CANCELLED, ((GeminiProvider.ProviderException)future.get(1,TimeUnit.SECONDS)).error); assertTrue(c.disconnected);
        } finally { worker.shutdownNow(); }
    }
    @Test public void globalDeadlineActivelyDisconnectsBlockedRead() throws Exception {
        Transport t = new Transport(); t.block = true;
        expect(GeminiProvider.Error.TIMEOUT, () -> new GeminiProvider(t, 100).generate(KEY,"gemini-test",GeminiOperation.REWRITE,null,null,"Hola",new Token())); assertTrue(t.opened.get(0).disconnected);
    }
    @Test public void modelDiscoveryUsesGetFiltersMethodsAndPaginates() throws Exception {
        Transport t = new Transport();
        t.bodies.add("{\"models\":[{\"name\":\"models/gemini-one\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/embedding\",\"supportedGenerationMethods\":[\"embedContent\"]}],\"nextPageToken\":\"next /+\"}");
        t.bodies.add("{\"models\":[{\"name\":\"models/gemini-two\",\"supportedGenerationMethods\":[\"generateContent\"]}]}");
        assertArrayEquals(new String[]{"gemini-one","gemini-two"},new GeminiProvider(t,2000).listModels(KEY,new Token()));
        assertEquals(2,t.opened.size()); Connection c = t.opened.get(0); assertEquals("GET",c.getRequestMethod()); assertEquals(0,c.request.size()); assertEquals(KEY,c.getRequestProperty("x-goog-api-key"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&pageToken=next+%2F%2B",t.opened.get(1).getURL().toString());
    }
}
