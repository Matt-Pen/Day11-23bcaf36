package in.edu.kristujayanti.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.internet.MimeMultipart;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.awt.SystemColor.text;

public class SampleService {
    secretclass srt=new secretclass();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("Eventbook");
    MongoCollection<Document> users = database.getCollection("Users");
    MongoCollection<Document> events = database.getCollection("Events");
    MongoCollection<Document> books = database.getCollection("Booking");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public void usersign(RoutingContext ctx) {
        //some more git test
        JsonObject signin = ctx.getBodyAsJson();
        String user = signin.getString("user");
        String name = signin.getString("name");

        ctx.response().setChunked(true);
        ctx.response().write("Password has been sent to your Email\n" + "Login using the password that has been sent");
        String pwd = generateID(8);
        sendemail(pwd, user);

        String hashpass = hashit(pwd);
        Document doc = new Document("name", name).append("user", user).append("pass", hashpass);
        InsertOneResult ins = users.insertOne(doc);

        if (ins.wasAcknowledged()) {
            ctx.response().end("Signed in successfully.");

        }
    }
    public void userlog(RoutingContext ctx) {
        JsonObject login = ctx.getBodyAsJson();
        JsonArray jarr = new JsonArray();
        String user = login.getString("user");
        String pwd = login.getString("pass");
        String hashlog = hashit(pwd);
        String status = "";
        ctx.response().setChunked(true);

        for (Document doc : users.find()) {
            String dbuser = doc.getString("user");
            String dbpass = doc.getString("pass");

            if (dbuser.equals(user)) {
                if (dbpass.equals(hashlog)) {
                    status = "Login was successfull";
                } else {
                    status = "Password is Incorrect";
                }
            } else {
                status = "Invalid Login Credentials";
            }
        }
        ctx.response().write(status + "\n");
        ctx.response().write("These are the Available courses:" + "\n");
        Bson projection = Projections.fields(Projections.exclude("_id"));
        for (Document doc : events.find().projection(projection)) {
            jarr.add(new JsonObject(doc.toJson()));
        }

        ctx.response().end(jarr.encodePrettily());

    }
    public void bookevent(RoutingContext ctx) {
        ctx.response().setChunked(true);
        String email = ctx.request().getParam("email");
        String event = ctx.request().getParam("event");
        Bson filter2 = Filters.regex("name", event);
        try {
            for (Document docs : events.find().filter(filter2)) {
                String evname = docs.getString("name");

                int st = docs.getInteger("seats");
                if (st < 1) {
                    ctx.response().write("Insufficient Seats.");
                    break;
                } else {
                    String tok = generateID(12);
                    BufferedImage qrimg = generateqr(tok);
                    sendQR(qrimg,email,evname);
                    Document doc = new Document("email", email).append("event", evname).append("token", tok);
                    InsertOneResult ins = books.insertOne(doc);
                    if (ins.wasAcknowledged()) {
                        ctx.response().write("Successfully booked");
                    }
                    st = st - 1;
                    Bson update2 = Updates.set("seats", st);
                    UpdateResult result2 = events.updateOne(filter2, update2);

                }
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
        ctx.response().end();
    }






    public static BufferedImage generateqr(String token)throws WriterException {
        int width=300;
        int height=300;
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode(token, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    public void sendQR(BufferedImage qrImage,String email,String eventname){
        String to = email;
        // provide sender's email ID
        String from = srt.from;

        // provide Mailtrap's username
        final String username = srt.username;
        final String password = srt.password;

        // provide Mailtrap's host address
        String host = "smtp.gmail.com";

        // configure Mailtrap's SMTP details
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        // create the Session object
        Session session = Session.getInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // Convert BufferedImage to ByteArray
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // Create MimeMessage
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("QR code for your booking of the named: "+ eventname);

            // Create message parts
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Use this QR Code upon entry to the venue.");

            MimeBodyPart imagePart = new MimeBodyPart();
            DataSource fds = new ByteArrayDataSource(imageBytes, "image/png");
            imagePart.setDataHandler(new DataHandler(fds));
            imagePart.setFileName("qrcode.png");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(imagePart);

            message.setContent(multipart);

            // Send message
            Transport.send(message);
            System.out.println("Email sent with QR code attached.");


            System.out.println("Email Message Sent Successfully!");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }



    public String hashit (String pass) {

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hashed = md.digest(pass.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed)
                sb.append(String.format("%02x", b));
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Hashing Failed");
        }
    }
    public static String generateID(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    public void sendemail(String pass,String email){
        String to = email;
        // provide sender's email ID
        String from = srt.from;

        // provide Mailtrap's username
        final String username = srt.username;
        final String password = srt.password;

        // provide Mailtrap's host address
        String host = "smtp.gmail.com";

        // configure Mailtrap's SMTP details
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        // create the Session object
        Session session = Session.getInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // create a MimeMessage object
            Message message = new MimeMessage(session);
            // set From email field
            message.setFrom(new InternetAddress(from));
            // set To email field
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // set email subject field
            message.setSubject("Use this Password to login to your Student Account.");
            // set the content of the email message
            message.setText("The Auto-generated password is: "+ pass);

            // send the email message
            Transport.send(message);

            System.out.println("Email Message Sent Successfully!");

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }

    }

    //Your Logic Goes Here
}
