package com.example.newnow.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LogManager.getLogger(EmailService.class);

    /**
     * Mock metoda — loguje email umesto slanja (Log4j2).
     */
    public void sendEmail(String to, String subject, String body) {
        logger.info("EMAIL POSLAT | TO: {} | SUBJECT: {} | BODY: {}", to, subject, body.replace("\n", " "));
    }


    public void sendAccountApprovedEmail(String email, String fullName) {
        String subject = "Vaš nalog je odobren - Novi Sad";
        String body = String.format(
                "Poštovani/a %s,\n\n" +
                        "Vaš zahtev za registraciju je odobren!\n" +
                        "Sada možete da se prijavite na aplikaciju.\n\n" +
                        "Srdačan pozdrav,\n" +
                        "Novi Sad Tim",
                fullName
        );
        sendEmail(email, subject, body);
    }


    public void sendAccountRejectedEmail(String email, String fullName) {
        String subject = "Vaš zahtev za registraciju je odbijen - Novi Sad";
        String body = String.format(
                "Poštovani/a %s,\n\n" +
                        "Nažalost, vaš zahtev za registraciju je odbijen.\n" +
                        "Za više informacija kontaktirajte administratora.\n\n" +
                        "Srdačan pozdrav,\n" +
                        "Novi Sad Tim",
                fullName
        );
        sendEmail(email, subject, body);
    }


    public void sendPasswordChangedEmail(String email, String fullName) {
        String subject = "Lozinka uspešno promenjena - Novi Sad";
        String body = String.format(
                "Poštovani/a %s,\n\n" +
                        "Vaša lozinka je uspešno promenjena.\n" +
                        "Ako niste Vi izvršili ovu promenu, odmah kontaktirajte administratora.\n\n" +
                        "Srdačan pozdrav,\n" +
                        "Novi Sad Tim",
                fullName
        );
        sendEmail(email, subject, body);
    }
}