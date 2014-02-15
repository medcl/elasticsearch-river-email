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

/**
 * @author medcl
 */
public class EmailRiverConfig {
	private String host;
	private int port=110;

    /**
     now support:
     imap
     imaps
     pop3s
     pop3
     */
	private String type="pop3";
	private String username;
	private String password;
	private String mailbox="INBOX";
	private int checkInterval;
	private int skipCount=0;
    private String idField="";

    private String attachmentIndex="mail_attachments";

    //use file's suffix as type
    private String attachmentDefaultType="no_suffix";

    private String weedFsServer="127.0.0.1";
    private int weedFsMasterPort=9333;
    private int weedFsVolumePort=8080;


    public EmailRiverConfig() {
	}
	
	public EmailRiverConfig(String host, int port,String type,String username,String password, int checkInterval,int skipCount,String weedfsHost,int weedfsMasterPort,int weedfsVolumePort) {
		this.setHost(host);
        this.setPort(port);
        this.setType(type);
        this.setUsername(username);
        this.setPassword(password);
        this.setCheckInterval(checkInterval);
        this.setSkipCount(skipCount);
        this.setWeedFsServer(weedfsHost);
        this.setWeedFsMasterPort(weedfsMasterPort);
        this.setWeedFsVolumePort(weedfsVolumePort);

	}

    public int getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(int checkInterval) {
        this.checkInterval = checkInterval;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(int skipCount) {
        this.skipCount = skipCount;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMailbox() {
        return mailbox;
    }

    public void setMailbox(String mailbox) {
        this.mailbox = mailbox;
    }

    public int getHashId(){
        return  Math.abs((getHost()+getPort()+getType()+getUsername()+getMailbox()).hashCode());
    }

    public String getIdField() {
        return idField;
    }


    public void setIdField(String idField) {
        this.idField = idField;
    }

    public String getAttachmentIndex() {
        return attachmentIndex;
    }

    public void setAttachmentIndex(String attachmentIndex) {
        this.attachmentIndex = attachmentIndex;
    }

    public String getAttachmentDefaultType() {
        return attachmentDefaultType;
    }

    public void setAttachmentDefaultType(String attachmentDefaultType) {
        this.attachmentDefaultType = attachmentDefaultType;
    }

    public String getWeedFsServer() {
        return weedFsServer;
    }

    public void setWeedFsServer(String weedFsServer) {
        this.weedFsServer = weedFsServer;
    }

    public int getWeedFsMasterPort() {
        return weedFsMasterPort;
    }

    public void setWeedFsMasterPort(int weedFsMasterPort) {
        this.weedFsMasterPort = weedFsMasterPort;
    }

    public int getWeedFsVolumePort() {
        return weedFsVolumePort;
    }

    public void setWeedFsVolumePort(int weedFsVolumePort) {
        this.weedFsVolumePort = weedFsVolumePort;
    }
}
