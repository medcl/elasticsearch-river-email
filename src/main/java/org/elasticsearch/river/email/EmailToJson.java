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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class EmailToJson {

    private static final Logger logger = LoggerFactory.getLogger(EmailToJson.class);

    static String[] convertAddress(Address[] input){

        String[] addrs1=new String[input.length];
        for (int i=0;i<input.length;i++){
            InternetAddress t= (InternetAddress) input[i];
            if(t!=null)
            {
                addrs1[i]= t.getAddress();
            }
        }
        return addrs1;
    }
    private static String stream2String( InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i;
        while ((i = is.read()) != -1) {
            baos.write(i);
        }
        return baos.toString();
    }

    private static boolean upload(String fileID, String publicUrl, byte[] data) {
        boolean result=false;
        if (data==null || data.length==0) {
            logger.error("upload file is null");
            return false;
        }

        //请求处理页面 "http://localhost:8080/"
        String uri = publicUrl+ fileID;

        logger.debug("prepare to upload file to:"+uri);

        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(uri);

        ByteArrayPartSource bp = new ByteArrayPartSource(fileID, data);

        FilePart bfp = new FilePart(fileID, bp);

//        bfp.setContentType("text/html");
//        bfp.setName("prompt:file");


        bfp.setTransferEncoding(null);
        org.apache.commons.httpclient.methods.multipart.Part[] parts = {bfp};
        postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));

        HttpClientParams params = new HttpClientParams();
        params.setConnectionManagerTimeout(5000L);
        httpClient.setParams(params);
        try {
            httpClient.executeMethod(postMethod);
            result=true;
        } catch (Exception e) {
            logger.error("上传文件失败！", e);
        } finally {
            postMethod.releaseConnection();
        }
        return result;
    }

    public static String uploadFileToWeedfs(byte[] data,EmailRiverConfig config) throws IOException {
        if (data==null||data.length==0) return null;

        String finalResult=null;

        String url="http://"+config.getWeedFsServer()+":"+config.getWeedFsMasterPort()+"/dir/assign?count=1"  ;

        GetMethod req = new GetMethod(url);
        int response;
        try {
            HttpClient httpClient = new HttpClient();
            response = httpClient.executeMethod(req);

            InputStream respStr;
            if (response == HttpStatus.SC_OK) {
                respStr = req.getResponseBodyAsStream();
                if (respStr != null) {
                    String resultStr = stream2String(respStr);
                    JSONObject json = JSON.parseObject(resultStr);
                    String fileid = json.get("fid").toString();

                    //String publicUrl = json.get("publicUrl").toString();
                    String publicUrl="http://"+config.getWeedFsServer()+":"+config.getWeedFsVolumePort()+"/";
                    boolean result=upload(fileid, publicUrl, data);
                    if(result){
                        finalResult=  fileid;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("上传文件失败！", e);
        } finally {
            req.releaseConnection();
        }
         return  finalResult;
    }

    public static List<AttachmentInfo> saveAttachmentToWeedFs(Part message,List<AttachmentInfo> attachments,EmailRiverConfig config) throws UnsupportedEncodingException, MessagingException,
            FileNotFoundException, IOException{
        if(attachments==null){
            attachments=new ArrayList<AttachmentInfo>();
        }
        boolean hasAttachment = false;
        try {
            hasAttachment = isContainAttachment(message);
        } catch (MessagingException e) {
            logger.error("save attachment",e);
        } catch (IOException e) {
            logger.error("save attachment", e);
        }
        if(hasAttachment){

            if (message.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) message.getContent();

                int partCount = multipart.getCount();
                for (int i = 0; i < partCount; i++) {

                    BodyPart bodyPart = multipart.getBodyPart(i);

                    String disp = bodyPart.getDisposition();
                    if (disp != null && (disp.equalsIgnoreCase(Part.ATTACHMENT) || disp.equalsIgnoreCase(Part.INLINE))) {
                        InputStream is = bodyPart.getInputStream();

                        BufferedInputStream bis = new BufferedInputStream(is);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();

                        int len = -1;
                        while ((len = bis.read()) != -1) {
                            bos.write(len);
                        }
                        bos.close();
                        bis.close();

                        byte[] data = bos.toByteArray();
                        String fileId=uploadFileToWeedfs(data,config);
                        if(fileId!=null){
                            AttachmentInfo info=new AttachmentInfo();
                            info.fileId=fileId;
                            info.fileName =decodeText(bodyPart.getFileName());
                            info.fileSize= data.length;
                            attachments.add(info);
                        }

                    } else if (bodyPart.isMimeType("multipart/*")) {

                      attachments.addAll(saveAttachmentToWeedFs(bodyPart, attachments,config));

                    } else {
                        String contentType = bodyPart.getContentType();
                        if (contentType.indexOf("name") != -1 || contentType.indexOf("application") != -1) {
                            attachments.addAll(saveAttachmentToWeedFs(bodyPart, attachments,config));
                        }
                    }
                }
            } else if (message.isMimeType("message/rfc822")) {

                attachments.addAll(saveAttachmentToWeedFs(message, attachments,config));

            }

        }
        return attachments;
    }

    public static XContentBuilder toJson(Message message, String riverName,EmailRiverConfig config) throws IOException {
        XContentBuilder out = null;
        MimeMessage msg=(MimeMessage)message;
        try {
            List<AttachmentInfo> attachments=null;
            boolean hasAttachment = isContainAttachment(message);
            if(hasAttachment){
               attachments=saveAttachmentToWeedFs(msg,attachments,config);
            }

            StringBuffer content = new StringBuffer(30);
            getMailTextContent(msg, content);

            int length = 200;
            String summary=delHTMLTag(content.toString());
            summary=(summary.length() > length ? summary.substring(0, length) + "..." : summary);

            out = jsonBuilder()
                .startObject()
                    .field("subject", getSubject(msg))
                    .field("sent_date", getSentDate(msg, null))
//                    .field("recv_date", message.getReceivedDate())
                    .field("from_mail", convertAddress(message.getFrom()))
                    .field("reply_to_mail", convertAddress(message.getReplyTo()))
                    .field("from", getFrom(msg))
                    .field("to", getReceiveAddress(msg, null))
                    .field("content_type", getContentType(msg))
                    .field("summary", summary)
                    .field("content", content.toString())
                    .field("priority", getPriority(msg))
                    .field("need_receipt", isReplySign(msg))
                    .field("size", (msg.getSize() /1024) + "kb")
                    .field("contain_attachment", hasAttachment)
                    .field("timestamp", new Date().getTime())
                    .field("via_river", riverName);
            if(attachments!=null&&attachments.size()>0){
                XContentBuilder array = out.field("attachments").startArray();
                for (int i = 0; i < attachments.size(); i++) {
                    AttachmentInfo info=attachments.get(i);
                    array.startObject()
                            .field("file_name",info.fileName)
                            .field("file_size",info.fileSize)
                            .field("file_id",info.fileId)
                            .field("summary",info.summary)
                            .endObject();
                }
                array.endArray();
            }
        } catch (MessagingException e) {
            logger.error("convert mail to json",e);
        }
        assert out != null;
        return out.endObject();
	}

    private static String getContentType(MimeMessage msg) {
        try {
            String type= msg.getContentType();
            String[] array= type.split(";");
            if(array.length>1){
                return array[0];
            }
        } catch (MessagingException e) {
            logger.error("get content type",e);
        }
         return "";
    }


    static String regEx_script="<script[^>]*?>[\\s\\S]*?<\\/script>";
    static String regEx_style="<style[^>]*?>[\\s\\S]*?<\\/style>";
    static String regEx_html="<[^>]+>";

    static Pattern p_script=Pattern.compile(regEx_script,Pattern.CASE_INSENSITIVE);

    static  Pattern p_style=Pattern.compile(regEx_style,Pattern.CASE_INSENSITIVE);
   static Pattern p_html=Pattern.compile(regEx_html,Pattern.CASE_INSENSITIVE);

    public static String delHTMLTag(String htmlStr){

        Matcher m_script=p_script.matcher(htmlStr);
        htmlStr=m_script.replaceAll("");


        Matcher m_style=p_style.matcher(htmlStr);
        htmlStr=m_style.replaceAll("");


        Matcher m_html=p_html.matcher(htmlStr);
        htmlStr=m_html.replaceAll("");

        return htmlStr.trim();
    }


    public static String getSubject(MimeMessage msg) throws UnsupportedEncodingException, MessagingException {
        return MimeUtility.decodeText(msg.getSubject());
    }


    public static String getFrom(MimeMessage msg) throws MessagingException, UnsupportedEncodingException {
        String from = "";
        Address[] froms = msg.getFrom();
        if (froms.length < 1)
            throw new MessagingException("没有发件人!");

        InternetAddress address = (InternetAddress) froms[0];
        String person = address.getPersonal();
        if (person != null) {
            person = MimeUtility.decodeText(person) + " ";
        } else {
            person = "";
        }
        from = person + "<" + address.getAddress() + ">";

        return from;
    }


    public static String getReceiveAddress(MimeMessage msg, Message.RecipientType type) throws MessagingException {
        StringBuilder receiveAddress = new StringBuilder();
        Address[] addresss = null;
        if (type == null) {
            addresss = msg.getAllRecipients();
        } else {
            addresss = msg.getRecipients(type);
        }

        if (addresss == null || addresss.length < 1)
           return  "";
           // throw new MessagingException("没有收件人!");
        for (Address address : addresss) {
            InternetAddress internetAddress = (InternetAddress)address;
            receiveAddress.append(internetAddress.toUnicodeString()).append(",");
        }

        receiveAddress.deleteCharAt(receiveAddress.length()-1); //删除最后一个逗号

        return receiveAddress.toString();
    }

    public static String getSentDate(MimeMessage msg, String pattern) throws MessagingException {
        Date receivedDate = msg.getSentDate();
        if (receivedDate == null)
            return "";

        if (pattern == null || "".equals(pattern))
            pattern = "yyyy-MM-dd HH:mm:ss E";

        return new SimpleDateFormat(pattern).format(receivedDate);
    }

    public static boolean isContainAttachment(Part part) throws MessagingException, IOException {
        boolean flag = false;
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            int partCount = multipart.getCount();
            for (int i = 0; i < partCount; i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String disp = bodyPart.getDisposition();
                if (disp != null && (disp.equalsIgnoreCase(Part.ATTACHMENT) || disp.equalsIgnoreCase(Part.INLINE))) {
                    flag = true;
                } else if (bodyPart.isMimeType("multipart/*")) {
                    flag = isContainAttachment(bodyPart);
                } else {
                    String contentType = bodyPart.getContentType();
                    if (contentType.indexOf("application") != -1) {
                        flag = true;
                    }

                    if (contentType.indexOf("name") != -1) {
                        flag = true;
                    }
                }

                if (flag) break;
            }
        } else if (part.isMimeType("message/rfc822")) {
            flag = isContainAttachment((Part)part.getContent());
        }
        return flag;
    }

    public static boolean isSeen(MimeMessage msg) throws MessagingException {
        return msg.getFlags().contains(Flags.Flag.SEEN);
    }

    public static boolean isReplySign(MimeMessage msg) throws MessagingException {
        boolean replySign = false;
        String[] headers = msg.getHeader("Disposition-Notification-To");
        if (headers != null)
            replySign = true;
        return replySign;
    }

    public static String getPriority(MimeMessage msg) throws MessagingException {
        String priority = "Normal";
        String[] headers = msg.getHeader("X-Priority");
        if (headers != null) {
            String headerPriority = headers[0];
            if (headerPriority.indexOf("1") != -1 || headerPriority.indexOf("High") != -1)
                priority = "Urgent";
            else if (headerPriority.indexOf("5") != -1 || headerPriority.indexOf("Low") != -1)
                priority = "NonUrgent";
            else
                priority = "Normal";
        }
        return priority;
    }

    public static void getMailTextContent(Part part, StringBuffer content) throws MessagingException, IOException {
        boolean isContainTextAttach = part.getContentType().indexOf("name") > 0;
        if (part.isMimeType("text/*") && !isContainTextAttach) {
            content.append(part.getContent().toString());
        } else if (part.isMimeType("message/rfc822")) {
            getMailTextContent((Part)part.getContent(),content);
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int partCount = multipart.getCount();
            for (int i = 0; i < partCount; i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                getMailTextContent(bodyPart,content);
            }
        }
    }

    public static void saveAttachment(Part part, String destDir) throws UnsupportedEncodingException, MessagingException,
            FileNotFoundException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();

            int partCount = multipart.getCount();
            for (int i = 0; i < partCount; i++) {

                BodyPart bodyPart = multipart.getBodyPart(i);

                String disp = bodyPart.getDisposition();
                if (disp != null && (disp.equalsIgnoreCase(Part.ATTACHMENT) || disp.equalsIgnoreCase(Part.INLINE))) {
                    InputStream is = bodyPart.getInputStream();
                    saveFile(is, destDir, decodeText(bodyPart.getFileName()));
                } else if (bodyPart.isMimeType("multipart/*")) {
                    saveAttachment(bodyPart,destDir);
                } else {
                    String contentType = bodyPart.getContentType();
                    if (contentType.indexOf("name") != -1 || contentType.indexOf("application") != -1) {
                        saveFile(bodyPart.getInputStream(), destDir, decodeText(bodyPart.getFileName()));
                    }
                }
            }
        } else if (part.isMimeType("message/rfc822")) {
            saveAttachment((Part) part.getContent(),destDir);
        }
    }


    private static void saveFile(InputStream is, String destDir, String fileName)
            throws FileNotFoundException, IOException {
        BufferedInputStream bis = new BufferedInputStream(is);
        BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(new File(destDir + fileName)));
        int len = -1;
        while ((len = bis.read()) != -1) {
            bos.write(len);
            bos.flush();
        }
        bos.close();
        bis.close();
    }

    public static String decodeText(String encodeText) throws UnsupportedEncodingException {
        if (encodeText == null || "".equals(encodeText)) {
            return "";
        } else {
            return MimeUtility.decodeText(encodeText);
        }
    }

}
