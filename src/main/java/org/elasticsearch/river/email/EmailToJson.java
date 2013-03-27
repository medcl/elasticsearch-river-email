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

import org.elasticsearch.common.xcontent.XContentBuilder;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class EmailToJson {

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

    public static XContentBuilder toJson(Message message, String riverName) throws IOException {
        XContentBuilder out = null;
        MimeMessage msg=(MimeMessage)message;
        try {

            boolean hasAttachment = isContainAttachment(message);
            if(hasAttachment){
            //TODO save attachments
            }

            StringBuffer content = new StringBuffer(30);
            getMailTextContent(msg, content);

            int length = 200;
            String summary=delHTMLTag(content.toString());
            summary=(summary.length() > length ? summary.substring(0, length) + "..." : summary.toString());

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
                    .field("summary", summary.toString())
                    .field("content", content.toString())
                    .field("priority", getPriority(msg))
                    .field("need_receipt", isReplySign(msg))
                    .field("size", (msg.getSize() /1024) + "kb")
                    .field("contain_attachment", hasAttachment)
                    .field("via_river", riverName);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
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
            e.printStackTrace();
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
        StringBuffer receiveAddress = new StringBuffer();
        Address[] addresss = null;
        if (type == null) {
            addresss = msg.getAllRecipients();
        } else {
            addresss = msg.getRecipients(type);
        }

        if (addresss == null || addresss.length < 1)
            throw new MessagingException("没有收件人!");
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
