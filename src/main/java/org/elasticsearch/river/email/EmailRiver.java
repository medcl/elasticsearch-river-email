/*
* Licensed to Medcl (the "Author") under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. Author licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.elasticsearch.river.email;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;

import javax.mail.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;


/**
* @author medcl
*/
public class EmailRiver extends AbstractRiverComponent implements River {

	private final Client client;

	private final String indexName;

	private final String typeName;

	private volatile ArrayList<Thread> threads;

	private volatile boolean closed = false;

	private final ArrayList<EmailRiverConfig> riverConfigs;

	@SuppressWarnings({ "unchecked" })
	@Inject
	public EmailRiver(RiverName riverName, RiverSettings settings, Client client)
			throws MalformedURLException {
		super(riverName, settings);
		this.client = client;

		if (settings.settings().containsKey("email")) {
			Map<String, Object> emailSettings = (Map<String, Object>) settings.settings().get("email");

			// Getting email items config
			boolean array = XContentMapValues.isArray(emailSettings.get("config"));
			if (array) {
				ArrayList<Map<String, Object>> emails = (ArrayList<Map<String, Object>>) emailSettings.get("config");
				riverConfigs = new ArrayList<EmailRiverConfig>(emails.size());
				for (Map<String, Object> config : emails) {
					String host = XContentMapValues.nodeStringValue(config.get("host"), "localhost");
					int port = XContentMapValues.nodeIntegerValue(config.get("port"), 110);
					String type = XContentMapValues.nodeStringValue(config.get("type"), "pop");
					String username = XContentMapValues.nodeStringValue(config.get("username"), "");
					String password = XContentMapValues.nodeStringValue(config.get("password"), "");
					int interval  = XContentMapValues.nodeIntegerValue(config.get("check_interval"), 15 * 60 * 1000);
					int skip_count  = XContentMapValues.nodeIntegerValue(config.get("skip_count"), 1);
					String weedfs_host  = XContentMapValues.nodeStringValue(config.get("weedfs_host"), "127.0.0.1");
                    int weedfs_master_port = XContentMapValues.nodeIntegerValue(config.get("weedfs_master_port"), 9333);
                    int weedfs_volume_port = XContentMapValues.nodeIntegerValue(config.get("weedfs_volume_port"), 8080);

                    riverConfigs.add(new EmailRiverConfig(host, port,type, username,password,interval,skip_count,weedfs_host,weedfs_master_port,weedfs_volume_port));
				}

			} else {
                riverConfigs = new ArrayList<EmailRiverConfig>();
                String host = XContentMapValues.nodeStringValue(emailSettings.get("host"), "localhost");
                int port = XContentMapValues.nodeIntegerValue(emailSettings.get("port"), 110);
                String type = XContentMapValues.nodeStringValue(emailSettings.get("type"), "pop");
                String username = XContentMapValues.nodeStringValue(emailSettings.get("username"), "");
                String password = XContentMapValues.nodeStringValue(emailSettings.get("password"), "");
                int interval  = XContentMapValues.nodeIntegerValue(emailSettings.get("check_interval"), 15 * 60 * 1000);
                int skip_count  = XContentMapValues.nodeIntegerValue(emailSettings.get("skip_count"), 1);
                String weedfs_host  = XContentMapValues.nodeStringValue(emailSettings.get("weedfs_host"), "127.0.0.1");
                int weedfs_master_port = XContentMapValues.nodeIntegerValue(emailSettings.get("weedfs_master_port"), 9333);
                int weedfs_volume_port = XContentMapValues.nodeIntegerValue(emailSettings.get("weedfs_volume_port"), 8080);
                riverConfigs.add(new EmailRiverConfig(host, port,type, username,password,interval,skip_count,weedfs_host,weedfs_master_port,weedfs_volume_port));
			}

		}else {
            riverConfigs = new ArrayList<EmailRiverConfig>();
        }

		if (settings.settings().containsKey("index")) {
			Map<String, Object> indexSettings = (Map<String, Object>) settings
					.settings().get("index");
			indexName = XContentMapValues.nodeStringValue(
					indexSettings.get("index"), riverName.name());
			typeName = XContentMapValues.nodeStringValue(
					indexSettings.get("type"), "email");
		} else {
			indexName = riverName.name();
			typeName = "email";
		}
	}

	@Override
	public void start() {
		if (logger.isInfoEnabled()) logger.info("Starting email river");
		try {
			client.admin().indices().prepareCreate(indexName).execute()
					.actionGet();
		} catch (Exception e) {
			if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
				// that's fine
			} else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
                try {
                    Thread.sleep(30*60*1000);
                } catch (InterruptedException e1) {
                    logger.error("email-river",e1);
                }
            } else {
				logger.warn("failed to create index [{}], disabling river...",
						e, indexName);
				return;
			}
		}

		threads = new ArrayList<Thread>(riverConfigs.size());
		int threadNumber = 0;
		for (EmailRiverConfig riverConfig : riverConfigs) {
			Thread thread = EsExecutors.daemonThreadFactory(settings.globalSettings(), "email_slurper_" + threadNumber)
                    .newThread(new EmailParser(riverConfig));
			thread.start();
			threads.add(thread);
			threadNumber++;
		}
	}

	@Override
	public void close() {
		if (logger.isInfoEnabled()) logger.info("Closing email river");
		closed = true;

		// We have to close each Thread
		if (threads != null) {
			for (Thread thread : threads) {
				if (thread != null) {
					thread.interrupt();
				}
			}
		}
	}

	private class EmailParser implements Runnable {
	    private EmailRiverConfig config;

        public EmailParser(EmailRiverConfig config) {

            this.config=config;

            if (logger.isInfoEnabled()){
                logger.info("creating email stream river [{}] for [{}] every [{}] ms",config.getUsername(),config.getHost(),config.getCheckInterval());
            }
		}

        @SuppressWarnings("unchecked")
		@Override
		public void run() {
            while (true) {
				if (closed) {
					return;
				}

                Properties props = new Properties();

                Session session = Session.getDefaultInstance(props, new Authenticator(){
                    @Override
                    public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(config.getUsername(), config.getPassword());
                    }

                });


                try {
                    Store store = session.getStore(config.getType());
                    store.connect(config.getHost(),config.getPort(), config.getUsername(), config.getPassword());

                    Folder folder = store.getFolder(config.getMailbox());


                    int count;//=folder.getMessageCount();


                    int skipCount=getLastStateFromRiver(config.getHashId());
                    logger.debug("get last skip_count:{}",skipCount);

                    while (true){
                        folder.open(Folder.READ_ONLY);

                        if (logger.isDebugEnabled()) logger.debug("message count:"+folder.getMessageCount());
                        if (logger.isDebugEnabled()) logger.debug("deleted message count:"+folder.getDeletedMessageCount());
                        if (logger.isDebugEnabled()) logger.debug("new message count:"+folder.getNewMessageCount());
                        if (logger.isDebugEnabled()) logger.debug("unread message count:"+folder.getUnreadMessageCount());
                        if (logger.isDebugEnabled()) logger.debug("get mode:"+folder.getMode());

                        count=folder.getMessageCount();
                        if (logger.isDebugEnabled()) logger.debug("skip count:"+skipCount+",count:"+count);
                        if(count>=skipCount){

                            Message message[] = folder.getMessages(skipCount,count);//from 1

                            BulkRequestBuilder bulk = client.prepareBulk();
                            try {
                                for (int i=0, n=message.length; i<n; i++) {

                                    if (logger.isDebugEnabled()) logger.debug("message id:"+message[i].getMessageNumber());
                                    if (logger.isDebugEnabled()) logger.debug(i + ": " + message[i].getFrom()[0]+ "\t" + message[i].getSubject());

                                    if(config.getIdField().isEmpty()){

                                        bulk.add(indexRequest(indexName).type(typeName).source(EmailToJson.toJson(message[i], riverName.getName(),config)));
                                    }else{
                                        String[] ids=message[i].getHeader(config.getIdField());
                                        String id="";
                                        if(ids.length>0){
                                              id=ids[0];
                                        }

                                        // Let's look if object already exists
                                        GetResponse oldMessage = client.prepareGet(indexName, typeName, String.valueOf(id)).execute().actionGet();
                                        if (!oldMessage.isExists()) {
                                            bulk.add(indexRequest(indexName).type(typeName).id(id).source(EmailToJson.toJson(message[i], riverName.getName(),config)));

                                            if (logger.isDebugEnabled()) logger.debug("Email update detected for Id [{}]",id);
                                            if (logger.isTraceEnabled()) logger.trace("Email is : {}", message[i].getSubject());
                                        } else {
                                            if (logger.isTraceEnabled()) logger.trace("Email {} already exist. Ignoring", id);
                                        }
                                    }
                                }

                                skipCount=count+1;

                                // We store the last skip_count
                                bulk.add(indexRequest("_river").type(riverName.name()).id(String.valueOf(config.getHashId()))
                                        .source(jsonBuilder().startObject().startObject("email").field("skip_count", skipCount).endObject().endObject()));
                            } catch (IOException e) {
                                logger.error("email-river",e);
                                logger.warn("failed to add email entry to bulk indexing");
                            }

                            if(bulk.numberOfActions()>0){
                                try {
                                    BulkResponse response = bulk.execute().actionGet();
                                    if (response.hasFailures()) {
                                        logger.warn("failed to execute" + response.buildFailureMessage());
                                    }
                                } catch (Exception e) {
                                    logger.error("email-river",e);
                                    logger.warn("failed to execute bulk", e);
                                }
                            }

                        }

                        folder.close(false);
                        store.close();

                        if (logger.isDebugEnabled()) logger.debug("sleeping {}",config.getUsername());
                        Thread.sleep(config.getCheckInterval());
                        if (logger.isDebugEnabled()) logger.debug("resuming {}",config.getUsername());

                        //refresh skip_count in order to apply the changes on the fly
                        skipCount=getLastStateFromRiver(config.getHashId());
                        store = session.getStore(config.getType());

                        if(!store.isConnected())
                        {
                            if (logger.isDebugEnabled()) logger.debug("reconnecting {}",config.getUsername());
                            store.connect(config.getHost(),config.getPort(), config.getUsername(), config.getPassword());
                            folder = store.getFolder(config.getMailbox());
                        }
                    }




                } catch (MessagingException e) {
                    logger.error("email-river",e);
                } catch (InterruptedException e) {
                    logger.error("email-river", e);
                }

                if (logger.isDebugEnabled()) logger.debug("GetMail Process Over!");

                }
			}


        @SuppressWarnings("unchecked")
		private int getLastStateFromRiver(int settingId) {
            int skipCount=1;
            try {
                client.admin().indices().prepareRefresh("_river").execute().actionGet();
                GetResponse lastSeqGetResponse =
                        client.prepareGet("_river", riverName().name(), String.valueOf(settingId)).execute().actionGet();
                if (lastSeqGetResponse.isExists()) {
                    Map<String, Object> rssState = (Map<String, Object>) lastSeqGetResponse.getSource().get("email");

                    if (rssState != null) {
                        Object skip_count = rssState.get("skip_count");
                        if (skip_count != null) {
                            skipCount= Integer.parseInt(skip_count.toString());
                        }
                    }
                } else {
                    if (logger.isDebugEnabled()) logger.debug("{} doesn't exist", settingId);
                }
            } catch (Exception e) {
                logger.error("email-river",e);
                logger.warn("failed to get last_skip_count, throttling....", e);
            }
           return skipCount;
        }
    }
}
