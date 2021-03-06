/*
 * Copyright (c) 2019 - Globo.com - ATeam
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globo.pepe.api.controller;

import static com.globo.pepe.common.util.Constants.PACK_NAME;
import static com.globo.pepe.common.util.Constants.TRIGGER_PREFIX;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fridujo.rabbitmq.mock.compatibility.MockConnectionFactoryFactory;
import com.globo.pepe.api.services.ChapolinService;
import com.globo.pepe.api.services.KeystoneService;
import com.globo.pepe.common.model.Metadata;
import com.globo.pepe.common.services.AmqpService;
import com.globo.pepe.common.services.JsonLoggerService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockserver.integration.ClientAndServer;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest({
    EventController.class,
    KeystoneService.class,
    ChapolinService.class,
    AmqpService.class,
    JsonLoggerService.class,
    ObjectMapper.class
})
@TestPropertySource(properties = {
    "pepe.logging.tags=default",
    "spring.pid.file=/tmp/pepe.pid",
    "pepe.stackstorm.api=http://127.0.0.1:9000/api",
    "pepe.stackstorm.auth=http://127.0.0.1:9000/auth",
    "pepe.stackstorm.stream=http://127.0.0.1:9000/stream"
})
public class EventControllerTests {

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AmqpService amqpService;

    @Autowired
    private KeystoneService keystoneService;

    private static ClientAndServer mockServer;

    private static final ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @BeforeClass
    public static void setupClass() throws IOException {
        mockServer = ClientAndServer.startClientAndServer(5000);

        InputStream resourceAuthOk = EventControllerTests.class.getResourceAsStream("/keystone-auth.json");
        String bodyAuthOk = IOUtils.toString(resourceAuthOk, Charset.defaultCharset());
        mockServer.when(request().withMethod("POST").withPath("/v3/auth/tokens").withBody(requestBody("token-ok")))
            .respond(response().withBody(bodyAuthOk).withHeader("Content-Type", APPLICATION_JSON_VALUE).withStatusCode(201));

        InputStream resourceAuthFail = EventControllerTests.class.getResourceAsStream("/keystone-auth-fail.json");
        String bodyAuthFail = IOUtils.toString(resourceAuthFail, Charset.defaultCharset());
        mockServer.when(request().withMethod("POST").withPath("/v3/auth/tokens").withBody(requestBody("wrong-token")))
            .respond(response().withBody(bodyAuthFail).withHeader("Content-Type", APPLICATION_JSON_VALUE).withStatusCode(401));

        String bodyKeystoneError = "";
        mockServer.when(request().withMethod("POST").withPath("/v3/auth/tokens").withBody(requestBody("force-error")))
            .respond(response().withBody(bodyKeystoneError).withHeader("Content-Type", APPLICATION_JSON_VALUE).withStatusCode(201));

    }

    @Before
    public void setup() {
        connectionFactory = new CachingConnectionFactory(MockConnectionFactoryFactory.build());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitAdmin = new RabbitAdmin(connectionFactory);
    }

    private static String requestBody(String token) {
        return "{\n"
            + "  \"auth\" : {\n"
            + "    \"identity\" : {\n"
            + "      \"token\" : {\n"
            + "        \"id\" : \"" + token + "\"\n"
            + "      },\n"
            + "      \"methods\" : [ \"token\" ]\n"
            + "    },\n"
            + "    \"scope\" : {\n"
            + "      \"project\" : {\n"
            + "        \"name\" : \"admin\",\n"
            + "        \"domain\" : {\n"
            + "          \"id\" : \"default\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
    }
    private JsonNode metadata(String project, String source, Long timestamp, String triggerName) {
        final Metadata metadata = new Metadata()
                .setProject(project)
                .setSource(source)
                .setTimestamp(timestamp)
                .setTriggerName(triggerName);
        return MAPPER.convertValue(metadata, JsonNode.class);
    }

    private JsonNode metadataWithCustomAttributes() {
        final ObjectNode customAttributes = MAPPER.createObjectNode()
                .put("component", "xxx")
                .put("subcomponent", "xxx");

        return ((ObjectNode)metadata()).set("custom_attributes", customAttributes);
    }

    private JsonNode metadata(String source, Long timestamp, String triggerName) {
        return metadata(null, source, timestamp, triggerName);
    }

    private JsonNode metadata(Long timestamp, String triggerName) {
        return metadata(null, null, timestamp, triggerName);
    }

    private JsonNode metadata(String source, String triggerName) {
        return metadata(null, source, null, triggerName);
    }

    private JsonNode metadata(String source, Long timestamp) {
        return metadata(null, source, timestamp, null);
    }

    private JsonNode metadata() {
        return metadata("admin", "asource", 0L, " ");
    }

    @AfterClass
    public static void cleanup() {
        if (mockServer.isRunning()) {
            mockServer.stop();
        }
    }

    @Test
    public void idMissingTest() throws Exception {
        String eventWithoutAuth = "{\"payload\":{},\"metadata\":" + metadata("asource", 0L, "atrigger") + "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void metadataMissingTest() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"payload\":{}}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void payloadMissingTests() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"metadata\":" + metadata("asource", 0L, "atrigger") + "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void metadataProjectMissingTest() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" +
            metadata(null, "asource", 0L, "atrigger") +
            "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
            .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void metadataSourceMissingTest() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata(0L, "atrigger") +
                "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void metadataTimestampMissingTest() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata("asource", "atrigger") +
                "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void metadataTriggerMissingTest() throws Exception {
        String eventWithoutAuth = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata("asource", 0L) +
                "}";
        mockMvc.perform(post("/event").content(eventWithoutAuth).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isBadRequest());
    }

    @Test
    public void eventControllerNotAuthenticationTest() throws Exception {
        setField(keystoneService, "securityDisabled", true);
        String eventWithAuthOK = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata() + "}";
        mockMvc.perform(post("/event").content(eventWithAuthOK).header("X-Auth-Token", "wrong-token")
            .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isCreated());
        setField(keystoneService, "securityDisabled", false);
    }

    @Test
    public void eventControllerAuthenticationOk() throws Exception {
        String eventWithAuthOK = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata() + "}";
        mockMvc.perform(post("/event").content(eventWithAuthOK).header("X-Auth-Token", "token-ok")
            .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isCreated());
    }

    @Test
    public void eventControllerAuthenticationFail() throws Exception {
        String eventWithAuthFail = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata() + "}";
        mockMvc.perform(post("/event").content(eventWithAuthFail).header("X-Auth-Token", "wrong-token")
            .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isUnauthorized());
    }

    @Test
    public void eventControllerKeystoneError() throws Exception {
        String eventWithAuthFail = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadata() + "}";
        mockMvc.perform(post("/event").content(eventWithAuthFail).header("X-Auth-Token", "force-error")
            .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isUnauthorized());
    }

    @Test
    public void customAttributesTest() throws Exception {
        String eventWithAuthOK = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadataWithCustomAttributes() + "}";
        mockMvc.perform(post("/event").content(eventWithAuthOK).header("X-Auth-Token", "token-ok")
                .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isCreated());
    }

    @Test
    public void customAttributesWithListenerTest() throws Exception {
        final String queueName = PACK_NAME + "." + TRIGGER_PREFIX + ".atrigger";
        amqpService.newQueue(queueName);
        amqpService.prepareListenersMap(queueName);
        amqpService.registerListener(queueName, (message, channel) -> System.out.println(new String(message.getBody())));

        String eventWithAuthOK = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadataWithCustomAttributes() + "}";
        int numEvents = 10;
        for (int i=0; i<numEvents; i++) {
            mockMvc.perform(post("/event").content(eventWithAuthOK).header("X-Auth-Token", "token-ok")
                    .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isCreated());
        }
    }

    @Test
    public void customAttributesWithListener2Test() throws Exception {
        final String queueName = PACK_NAME + "." + TRIGGER_PREFIX + ".atrigger";
        amqpService.newQueue(queueName);
        amqpService.prepareListenersMap(queueName);

        String eventWithAuthOK = "{\"id\":\"xxx\",\"payload\":{},\"metadata\":" + metadataWithCustomAttributes() + "}";
        int numEvents = 10;
        for (int i=0; i<numEvents; i++) {
            mockMvc.perform(post("/event").content(eventWithAuthOK).header("X-Auth-Token", "token-ok")
                    .contentType(APPLICATION_JSON_VALUE)).andExpect(status().isCreated());
        }
    }
    
    @Test
    public void corsTest() throws Exception {
        mockMvc.perform(get("/healthcheck").header("Origin", "http://xxx"))
            .andExpect(status().isOk())
            .andDo(print());
    }

    @Test
    public void corsFailTest() throws Exception {
        mockMvc.perform(get("/healthcheck").header("Origin", "http://yyy"))
            .andExpect(status().isForbidden())
            .andDo(print());
    }

}
