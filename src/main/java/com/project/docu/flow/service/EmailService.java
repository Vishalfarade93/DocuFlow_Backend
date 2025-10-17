package com.project.docu.flow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.from}")
    private String fromAddress;
    @Async
    public void sendEmail(String to, String subject, String text) {
        if (mailSender == null) {
           
            System.err.println(" MailSender not configured properly!");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null) {
                message.setFrom(fromAddress);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            System.out.println(" Email sent successfully to " + to + " (subject: " + subject + ")");
        } catch (Exception e) {
          
            e.printStackTrace();
        }
    }
}
