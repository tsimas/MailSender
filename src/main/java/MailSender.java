import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.stringtemplate.v4.*;

/**
 * Created by ex63046 on 2017.04.11..
 */
public class MailSender {

    private final static Logger logger = Logger.getLogger(MailSender.class.getName());
    static FileHandler logFileHandler;

    public static Logger getLogger() {
        return logger;
    }

    public static void main(String[] args){

        Map<String, String> emailProperties;
        Map<String, String> accountProperties;

        try{
            // logger
            Util.initLogger(Paths.get(".", "MailSender.log").toAbsolutePath().toString());

            // properties
            emailProperties = Util.parseProperties(Paths.get(".", "email.properties").toAbsolutePath().toString());
            accountProperties = Util.parseProperties(Paths.get(".", "email_account.properties").toAbsolutePath().toString());

            Util.printProperties(emailProperties);
            Util.printProperties(accountProperties);

            // recipients
            List<String> recipients = Util.parseRecipients(emailProperties.get("recipientsSource"));
            Util.printRecipients(recipients, "Recipients");

            // already sent recipients
            List<String> alreadySentRecipients = Util.parseAlreadySentRecipients(emailProperties.get("alreadySentRecipientsSource"));
            Util.printRecipients(alreadySentRecipients, "Already sent recipients");
            List<String> newAlreadySentRecipients = new ArrayList<>();

            // bad emails
            List<String> badEmails = Util.parseBadEmails(emailProperties.get("badEmailAddresses"));
            Util.printRecipients(badEmails, "Bad e-mail addresses");
            List<String> newBadEmails = new ArrayList<>();

            int packageSize = Integer.parseInt(emailProperties.get("packageSize"));

            try{
                Util.sendEmails(emailProperties, accountProperties, recipients,
                        alreadySentRecipients, newAlreadySentRecipients, badEmails, newBadEmails, packageSize);
            } catch (Exception e) {
                throw  e;
            } finally {
                // write alreadySentEmails
                if (newAlreadySentRecipients.size() > 0) {
                    Util.writeToFile(newAlreadySentRecipients, Paths.get(".", emailProperties.get("alreadySentRecipientsSource")));
                }

                // write bad emails
                if (newBadEmails.size() > 0) {
                    Util.writeToFile(newBadEmails, Paths.get(".", emailProperties.get("badEmailAddresses")));
                }
            }



        } catch (Exception e) {
            e.printStackTrace();
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            logger.severe(e.getMessage() + " : "  + errors);
        }
    }


    // UTIL
    public static class Util {

        static Function<String, String> transforRecipientEmailToName = (email) ->  email.substring(0, email.indexOf('@'));

        public static void sendEmails(
                Map<String,
                String> emailProperties,
                Map<String, String> accountProperties,
                List<String> recipients,
                List<String> alreadySentRecipients,
                List<String> newAlreadySentRecipients,
                List<String> badEmails,
                List<String> newBadEmails,
                int packageSize) throws Exception{
            // params to email body template
            String senderName = emailProperties.get("senderName");

            int counter = 0;

            STGroup templateGrp = new STGroupDir(Paths.get("." ).normalize().toAbsolutePath().toString(), "UTF-8", '{', '}');

            try {

                Map<String, String> mailData = new HashMap<>();
                mailData.put("subject", emailProperties.get("subject"));
                mailData.put("senderEmail", accountProperties.get("email"));
                mailData.put("username", accountProperties.get("username"));
                mailData.put("password", accountProperties.get("password"));

                SendMailSSL sendMail = new SendMailSSL(mailData);
                for (String recipientLine : recipients) {

                    if (counter >= packageSize) {
                        break;
                    }

                    if (alreadySentRecipients.contains(recipientLine) || newAlreadySentRecipients.contains(recipientLine)) {
                        logger.info("Mail already sent to " + recipientLine + ". Don't send it again.");
                        continue;
                    }

                    if (badEmails.contains(recipientLine) || newBadEmails.contains(recipientLine)) {
                        logger.info("Mail is a bad email " + recipientLine + ". Don't send it again.");
                        continue;
                    }

                    try {

                        String recipientName = recipientLine.split("\t")[0];
                        String recipientEmail = recipientLine.split("\t")[1];

                        String tplName = emailProperties.get("emailBodyTemplate");
                        ST emailBodyTemplate = templateGrp.getInstanceOf(tplName);
                        emailBodyTemplate.add("senderName", senderName);
                        emailBodyTemplate.add("recipientName", recipientName);
                        String parsedBody = emailBodyTemplate.render();


                        mailData.put("body", parsedBody);
                        mailData.put("recipientEmail", recipientEmail);


                        sendMail.send(mailData);

                        try {
                            Thread.sleep(1000);                 //1000 milliseconds is one second.
                        } catch(InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                        // add to sent mails
                        newAlreadySentRecipients.add(recipientLine);

                        counter++;
                    } catch (Exception e) {
                        logger.severe("Error during sending mail :: " + e.getMessage());
                        e.printStackTrace();
                        newBadEmails.add(recipientLine);
                        //throw e;
                    }
                }
            } catch (Exception e) {
                throw e;
            } finally  {
                logger.info("Sent messages (pieces) : " + counter);
            }

        }

        public static void initLogger(String fileName) throws  Exception{

            // This block configure the logger with handler and formatter
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s %5$s %6$s%n");

            logFileHandler = new FileHandler(fileName);
            logger.addHandler(logFileHandler);
            SimpleFormatter formatter = new SimpleFormatter();
            logFileHandler.setFormatter(formatter);
        }

        public static Map<String, String> parseProperties(String propertyName) throws Exception{
            Properties properties = new Properties();
            Map<String, String> map = new HashMap<>();

            try( InputStream inputstream = new FileInputStream(propertyName) ) {

                properties.load(inputstream);

            } catch (Exception e) {
                throw  e;
            }

            for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
                map.put((String) entry.getKey(), (String) entry.getValue());
            }

            return map;
        }

        public static void printProperties(Map<String, String> properties) throws  Exception {
            logger.info("----------------------Properties--------------------");
            for ( Map.Entry<String, String>  entry : properties.entrySet()) {
                logger.info(entry.getKey() + " :: " +  entry.getValue());
            }
            logger.info("----------------------End Properties--------------------");
        }

        public static void printRecipients(List<String> recipients, String title) throws  Exception {
            logger.info("----------------------" + title + "--------------------");
            for ( String recipient : recipients) {
                logger.info(recipient);
            }
            logger.info("----------------------End " + title + "--------------------");
        }

        public static List<String> parseRecipients(String fileName) throws Exception{
            return parseEmailAdressesFromFile(fileName);
        }

        public static List<String> parseAlreadySentRecipients(String fileName) throws Exception{
            return parseEmailAdressesFromFile(fileName);
        }

        public static List<String> parseBadEmails(String fileName) throws Exception{
            return parseEmailAdressesFromFile(fileName);
        }

        public static List<String> parseEmailAdressesFromFile(String fileName) throws Exception{
            logger.info(fileName);
            Path path = Paths.get(".", fileName);

            List list;
            try (Stream<String> stream = Files.lines(Paths.get(fileName))) {

                list = stream
                        .map(String :: trim)
                        .map(line -> line.replace("\uFEFF", ""))
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toList());

            } catch (IOException e) {
                throw e;
            }

            return list;
        }

        public static void writeToFile(List<String> lines, Path path) throws Exception{
            Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

    }

}
