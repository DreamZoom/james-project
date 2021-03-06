/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailets;

import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.Forward;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.NotifyPostmaster;
import org.apache.james.transport.mailets.NotifySender;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.Resend;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class BounceIntegrationTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int SMTP_PORT = 1025;
    private static final int IMAP_PORT = 1143;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    public static final String POSTMASTER = "postmaster@" + JAMES_APACHE_ORG;
    public static final String BOUNCE_RECEIVER = "bounce.receiver@" + JAMES_APACHE_ORG;
    private static final String RECIPIENT = "to@" + JAMES_APACHE_ORG;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;
    private DataProbe dataProbe;


    @Before
    public void setup() throws Exception {
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void dsnBounceMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(DSNBounce.class)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(BOUNCE_RECEIVER, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void bounceMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Bounce.class)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(BOUNCE_RECEIVER, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void forwardMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Forward.class)
                .addProperty("forwardTo", BOUNCE_RECEIVER)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage("any@" + JAMES_APACHE_ORG, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void redirectMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Redirect.class)
                .addProperty("recipients", BOUNCE_RECEIVER)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage("any@" + JAMES_APACHE_ORG, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void resendMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(Resend.class)
                .addProperty("recipients", BOUNCE_RECEIVER)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage("any@" + JAMES_APACHE_ORG, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void notifySenderMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(NotifySender.class)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(BOUNCE_RECEIVER, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(BOUNCE_RECEIVER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    @Test
    public void notifyPostmasterMailetShouldDeliverBounce() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder,
            generateMailetContainerConfiguration(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(NotifyPostmaster.class)
                .addProperty("passThrough", "false")));
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(POSTMASTER, PASSWORD);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage("any@" + JAMES_APACHE_ORG, RECIPIENT);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() ->
                imapMessageReader.userReceivedMessageInMailbox(POSTMASTER, PASSWORD, MailboxConstants.INBOX));
        }
    }

    private MailetContainer generateMailetContainerConfiguration(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return MailetContainer.builder()
            .postmaster(POSTMASTER)
            .threads(5)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(transport())
            .addProcessor(bounces(redirectionMailetConfiguration))
            .build();
    }

    private ProcessorConfiguration transport() {
        // This processor delivers emails to BOUNCE_RECEIVER and POSTMASTER
        // Other recipients will be bouncing
        return ProcessorConfiguration.builder()
            .state("transport")
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoveMimeHeader.class)
                .addProperty("name", "bcc")
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(VacationMailet.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(BOUNCE_RECEIVER)
                .mailet(LocalDelivery.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(POSTMASTER)
                .mailet(LocalDelivery.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", "bounces")
                .build())
            .build();
    }

    public static ProcessorConfiguration bounces(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return ProcessorConfiguration.builder()
            .state("bounces")
            .addMailet(redirectionMailetConfiguration.build())
            .build();
    }
}
