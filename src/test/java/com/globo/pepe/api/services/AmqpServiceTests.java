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

package com.globo.pepe.api.services;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.globo.pepe.api.mocks.AmqpMockConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {AmqpService.class, AmqpMockConfiguration.class}, loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = {
        "amqp.url=amqp://guest:guest@127.0.0.1"
})
public class AmqpServiceTests {

    @Autowired
    public AmqpService amqpService;

    @Test
    public void connectionFactoryIsMock() {
        assertTrue(((AbstractConnectionFactory)amqpService.connectionFactory()).getRabbitConnectionFactory() instanceof MockConnectionFactory);
    }

    @Ignore
    @Test
    public void sendMessageTest() throws InterruptedException {
        String queueName="test";
        String originalMessage="message";
        amqpService.newQueue(queueName);
        amqpService.startListeners(queueName);
        CountDownLatch latch = new CountDownLatch(1);
        amqpService.registerListener(queueName, message -> {
            assertEquals("not original message", originalMessage, new String(message.getBody()));
            latch.countDown();
        });

        amqpService.convertAndSend(queueName, originalMessage);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        amqpService.stopListener(queueName);
    }
}
