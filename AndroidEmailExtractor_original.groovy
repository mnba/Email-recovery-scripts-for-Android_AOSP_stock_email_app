/**
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.  This file is offered as-is,
 * without any warranty.
 *
 * Script to take an Android ADB email dump and process emails to .eml RFC822 
 * format files. Does not handle attachments/multipart at present.
 *
 * <em>Notes:</em> 
 * Configured for a specific account and the Sent items mailbox 
 * in the 1st query.
 *
 * @author Robin Bramley (c) 2014
 */

@Grapes([
 @Grab(group='org.xerial',module='sqlite-jdbc',version='3.7.2'),
 @GrabConfig(systemClassLoader=true)
])

@Grapes(
    @Grab(group='com.icegreen', module='greenmail', version='1.3')
)
// lazy way of pulling in JavaMail & Glasgow JAF (showing my age?)

import java.sql.*
import javax.mail.internet.*;
import javax.mail.*
import javax.activation.*

import groovy.sql.Sql

import org.sqlite.SQLite

// Configurable variables
def mailFrom = 'foo@example.com'
def accountKey = 1
def mailboxKey = 5

// utility to convert addresses into InternetAddress[]
def convertAddresses = { inList ->
    def cleanListStr = ""
    def i = 0
    def dirtyList = inList.tokenize('\u0001') // split on SOH

    dirtyList.each {
        if(it.contains("@")) {
            // set list separator when required
            if(i > 0) {
                cleanListStr += ','
            }
            
            // clear any text, we'll just use the email address
            if(it.contains('\u0002')) { // STX
                def stxIndex = it.indexOf(2)
                it = it.substring(0, stxIndex)
            }

            cleanListStr += it
            i++
        }
    }

    return addresses = InternetAddress.parse(cleanListStr, false) // strict
}

// connection handles for finally block
def sql, sqlBody

try {
    def props = new Properties()
    
    // get mail session
    def session = Session.getDefaultInstance(props)
    
    // get the DBs
    sql = Sql.newInstance('jdbc:sqlite:EmailProvider.db', 'org.sqlite.JDBC')
    sqlBody = Sql.newInstance('jdbc:sqlite:EmailProviderBody.db', 'org.sqlite.JDBC')
    
    // get the headers
    sql.eachRow("""
    SELECT _id as msgKey, timeStamp, subject, messageId, fromList, toList, ccList, bccList, replyToList, threadId
    FROM Message
    WHERE accountKey = ${accountKey} and mailboxKey = ${mailboxKey}
    ORDER BY timeStamp ASC
    """) { row -> 
        def msgKey = row.msgKey
        
        // get the message body
        def body = sqlBody.firstRow("SELECT textContent, textReply FROM Body WHERE messageKey = ${msgKey}")
        
        // construct the email
        def msg = new MimeMessage(session)
        msg.setFrom(new InternetAddress(mailFrom))
        msg.setSubject(row.subject)
        msg.setSentDate(new Date(row.timeStamp))
        
        if (row.toList) {
            msg.setRecipients(Message.RecipientType.TO, convertAddresses(row.toList))
        }
        if (row.ccList) {
            msg.setRecipients(Message.RecipientType.CC, convertAddresses(row.ccList))
        }
        if (row.bccList) {
            msg.setRecipients(Message.RecipientType.BCC, convertAddresses(row.bccList))
        }
        
        msg.setText(body.textContent + '\n\n-------------------------\n\n' + body.textReply)
        
        // Write out the email
        new File("${row.timestamp}-${msgKey}.eml").withOutputStream { os ->
            msg.writeTo(os)
        }
    }
} catch (Exception e) {
    println e
} finally {
    if(sql) { sql.close() }
    if(sqlBody) { sqlBody.close() }
}