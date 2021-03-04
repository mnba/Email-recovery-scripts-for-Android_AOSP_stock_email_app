//#!/usr/bin/env groovy
/**
 *  AndroidEmailExtractor.groovy 
 * From:
 * Basic text: https://leanjavaengineering.wordpress.com/2014/06/10/android-email-extraction-to-eml/
 *  code: AlenPoulain:https://gist.github.com/alanpoulain/f1133132e0665e675c88
 *  original code: RBramley: https://gist.github.com/rbramley/65261127dfb857b03bb6
 * Additional script:
     : 
 * Script to take an Android ADB email dump and process emails to .eml RFC822 
 * format files. Does not handle attachments/multipart at present.
 *
 * <em>Notes:</em> 
 * Configured for a specific account and the Sent items mailbox 
 * in the 1st query.
 * 
 * This file is offered as-is, without any warranty.
 * @author Robin Bramley (c) 2014
 * Script to list accounts/mailboxes from an Android ADB email dump.
 * @author MA 2019 + // added body in file version + many other things
 * @author Alan Poulain 2014 //small tweaks
 * @author Robin Bramley (c) 2014 2015 //original author for body-in-db version 
   groovyc:  https://groovy-lang.org/groovyc.html
Run in command line:  
compile and run: Windows:bash:runner_compiled.sh [
PATH=$PATH:/d/bintmp/groovy-2.5.7/bin/
export PATH
mkdir -p /d/Data-Windows10/binout
cd /d/Data-Windows10/binout
groovyc ../AndroidEmailExtractor.groovy
] 
Simple run: // Windows:bash : runner1.sh [
export PATH=$PATH:/d/bintmp/groovy-2.5.7/bin/
cd /d/Data-Windows10/binout
groovy ../AndroidEmailExtractor.groovy
]
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

//import groovy.transform.Field
 
// Configurable variables
//def 
path_base = 'D:\\Data-Windows10\\Email-android-client-data_2019-6-28\\com-android-email-data-2019-6-28\\'
path_email_data = path_base + 'com.android.email\\'
path_email_db   =  path_email_data+'databases\\'  //path for email databases 
path_email_files = path_email_data+'files\\'
path_output_emls = path_base+'output1\\'

// Input params:

// Test params : select input params within DB/mail-acc, mail-folder
g_accountKey = 1 
g_mailboxKey = 5 //Worked OK when==5 //1 //TMP: 1==Inbox 5 ==Trash


// Utility to convert addresses into InternetAddress[]
//def convertAddresses = { inList ->
def convertAddresses(inList) //smth wrong 
{
    //types: inList:String dirtyList:ArrayList 
    def dirtyList = inList.tokenize('\u0001') // split on SOH
    
    def i = 0
    def cleanListStr = ""
    dirtyList.each {
        if (!it.contains("@")) {
          i++;
          return; //continue!
        }

        // Set list separator when required
        if (i > 0) 
            cleanListStr += ','

        // Clear any text, we'll just use the email address
        if (it.contains('\u0002')) { // STX
            def stxIndex = it.indexOf(2)
            it = it.substring(0, stxIndex)
        }

        cleanListStr += it
        i++
    }

    return addresses = InternetAddress.parse(cleanListStr, false) // strict
}

def getMailboxMessagesCount(accountID, mailboxID) 
{
  def sql 
  try{
  // Connection handles for finally block
   sql = Sql.newInstance("jdbc:sqlite:${path_email_db}EmailProvider.db", 'org.sqlite.JDBC')
   // https://stackoverflow.com/questions/22684993/how-to-get-row-count-in-groovy-sql
   //def cnt = sql.firstRow('SELECT COUNT(*) AS cnt FROM form_tbl WHERE form_id=:id', id:1)?.cnt
   long cnt = sql.firstRow("""
     SELECT count(*) as count
     FROM Message 
     WHERE accountKey = ${accountID} and mailboxKey = ${mailboxID} 
     """, )?.count 
     return cnt;
  } catch (Exception e) {
    println "Error: getMailboxMessagesCount: $e"
    e.printStackTrace();
  } finally {
     if(sql) { 
      sql.close() 
     }
  }
}

/**
 https://stackoverflow.com/questions/13986692/retrieve-the-generated-message-id-before-sending-email-using-spring-javamail/13986946
 */
public class MimeMessagewID extends MimeMessage {
   private String messageID;

   public MimeMessagewID(Session session, String messageID) {
      super(session);
      this.messageID = messageID;
   }

   @Override
   protected void updateMessageID() throws MessagingException {
      setHeader("Message-ID", messageID);
   }  
}

/** main exporter worker function 

Used knowledge:
 'Java source codes'-type code storages and code vaults. 
 ggl: java multiPart addBodyPart set content type
java - How to set MimeBodyPart ContentType to "text/html"? - Stack Overflow
https://stackoverflow.com/questions/5028670/how-to-set-mimebodypart-contenttype-to-text-html

:param:   
  offset - the 1-based offset for the first row to be processed
    //sqlite offset is 1-based positive index
  count== maxRows - the maximum number of rows to be processed
*/
def exportEmailMessages(accountKey, mailboxKey, offset=1, count=0) //, outformat)
{ 
// Connection handles for finally block
def sql, sqlBody

try {
    def props = new Properties()

    // Get mail session
    def session = Session.getDefaultInstance(props)

    // Get the DBs
    //  check the file existence and permissions
    fpath1= "${path_email_db}EmailProvider.db"
    //-println "DBG: sqlconnstr=jdbc:sqlite:$fpath1"
    def filechk = new File(fpath1)
    assert filechk.exists() : "file not found:'$fpath1'"
    assert filechk.canRead() : "file cannot be read:'$fpath1'"

    int offsetpos //offset positive 
    int total_cnt 
    //sqlite offset is 1-based positive index
    if (offset>=0)
      offsetpos = offset
    else {
      total_cnt= getMailboxMessagesCount(accountKey, mailboxKey);
      offsetpos = (int)total_cnt + offset +1; //offset if 1-based index
    }

    //
    sql = Sql.newInstance("jdbc:sqlite:${path_email_db}EmailProvider.db", 'org.sqlite.JDBC')
    sqlBody = Sql.newInstance("jdbc:sqlite:${path_email_db}EmailProviderBody.db", 'org.sqlite.JDBC')

    int no= 1 //msg number, start counting from 1. 
    // Get email msg headers
    // http://docs.groovy-lang.org/latest/html/api/groovy/sql/Sql.html 
    //sql.eachRow("""...""", row-> ); 
    /*eachRow​(String sql, int offset, int maxRows, Closure closure)    
    Performs the given SQL query calling the given closure with each row of the result set starting 
    at the provided offset, and including up to maxRows number of rows.
    */
    if (sql== null) {
      println "BadError! sql==null" //?!
      return ;
    }
    println "pre-cycle: "+ total_cnt+" "+ offsetpos +" "+ count
    // sql-query & cycle:
    sql.eachRow("""
     SELECT _id as msgKey, timeStamp, subject, messageId, fromList, displayName, toList, ccList, bccList, replyToList 
     FROM Message 
     WHERE accountKey = ${accountKey} and mailboxKey = ${mailboxKey} 
     ORDER BY timeStamp ASC 
     """, offsetpos, count) { row -> /*any comment*/

        if (row ==null) {
          println "BadError! row==null" //this occurs on "bad mailboxes" on INBOX for example; but not on trash mail folder.
          return; //=== continue;
        }

        def msgKey = row.msgKey

        //TODO: Optimization (FIXME?) : extract this next line out of cycle and test whether
        //      EmailProviderBody.db / sqlBody contains, provides the body text.  
        // Get the message body
        def body = sqlBody.firstRow("SELECT htmlContent, textContent, textReply FROM Body WHERE messageKey = ${msgKey}")
        if (0)
          if (body.htmlContent== null && body.textContent==null) {
            //textReply==null;
            println "Error:!skipping because of WIP: Body is empty for messageKey=${msgKey}";
            }
        
        mailFrom = row.fromList //row.fromList [0]
        //==mailFrom=${mailFrom} 
        def date= new Date(row.timeStamp) //for debug next line. 
        println " DBG: fromList=${row.fromList} subject=${row.subject} row.timeStamp=${row.timeStamp}=$date displayName=${row.displayName} Message-ID=${row.messageId} row=$row"
        // Construct the email 
        def msg = new MimeMessagewID(session, row.messageId) //new MimeMessage(session)

        if (mailFrom==null) {
          println "Error: Invalid message: msgKey=${msgKey} ; skipping"
          println " no= $no"
          no +=1;
          return; //continue
        }
        //msg.setHeader("Message-ID", row.messageId); //      
        //FIXME: setFrom name : take  row.displayName
        iaFrom = new InternetAddress(mailFrom)
        iaFrom.setPersonal(row.displayName, "utf-8")
        msg.setFrom(iaFrom)
        // https://stackoverflow.com/questions/3451256/javamail-changing-charset-of-subject-line
        msg.setSubject(row.subject, "utf-8")
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
        
        //-println "ckpt4 $body,\n msg= $msg"
        // Set the HTML content if it exists
        if (body.htmlContent) {
            println "ckpt5 text/html"
            def contentType = 'text/html'
            msg.setContent(body.htmlContent, contentType)
        } else if ( body.textContent) {
            println "ckpt6 -text/plain"
            msg.setText(body.textContent) //doesn't work 
            println "ckpt6e"
        }
        else {
          // Read the body from file - for newer version of email client 
          //  all methods: https://www.baeldung.com/groovy-file-read
          //   https://stackoverflow.com/questions/7729302/how-to-read-a-file-in-groovy-into-a-string
          //long messageId = (long) row.messageId; //REAL Message-ID: field from header 
          long messageId = Long.valueOf( msgKey) //row.messageId) //== from String 
          //println "ckpt7.3, msgKey="+ msgKey 
          def (File bodyTextFile, File bodyHtmlFile) = getBodyFilesTextHtml(path_email_files, messageId);

          // get contents of two files:
          absentfilemask=0 //Absent files mask; 0 both exist, 1 text vsn absent 2 html vsn absent 3 both absent
                   
          String textContent = null;
          String htmlContent = null;
          try{
            textContent = bodyTextFile.getText('UTF-8') 
          }catch(e){
            //println "  dbg:textContent File not found, msgid="+messageId.toString()
            absentfilemask+= 1
          }
          try{
           htmlContent = bodyHtmlFile.getText('UTF-8')
          }catch(e) {
            //println "  dbg:htmlContent File not found, msgid="+messageId.toString()
            absentfilemask+= 2
          }
          if (absentfilemask>=3)
           println "Error: message body file was not found, both html&text, msgid="+messageId.toString() + " Absent files mask="+absentfilemask
          //-println "ckpt7d"

          try{
          //if (1) { // testing 
          if(absentfilemask==0) { //both text and html are here:
           Multipart multiPart = new MimeMultipart(); 
           //handling multipart: 
           if ( textContent !=null ) { 
              MimeBodyPart textPart = new MimeBodyPart();
              textPart.setHeader("Content-Type", "text/plain");
              textPart.setText(textContent, "UTF-8");
              multiPart.addBodyPart(textPart);
           }
           if ( htmlContent !=null ) {
             MimeBodyPart htmlPart = new MimeBodyPart();
             htmlPart.setHeader("Content-Type", "text/html");
             htmlPart.setContent(htmlContent, "text/html; charset=utf-8");
             multiPart.addBodyPart(htmlPart);
             //htmlPart.setHeader("Content-Type", "text/html");
             //htmlPart.setContent(htmlContent, "text/html");
           }
           msg.setContent(multiPart); 
           //----msg.setContent(multiPart, "text/html"); 
         }
         else if(absentfilemask==2) { // text part is here, html is absent
            msg.setHeader("Content-Type", "text/plain");
            msg.setText(textContent, "UTF-8");
         }
         else if(absentfilemask==1) { // html part is here, text is absent
            msg.setHeader("Content-Type", "text/html");
            msg.setContent(htmlContent, "text/html; charset=utf-8");
         }

          } catch (MessagingException e) {
            println "Error: "+e.toString()
            e.printStackTrace();
          }

          msg.saveChanges();

          println " done msgid="+msgKey.toString()
        }
        
        // Write out the email== .eml file 
        new File("${path_output_emls}/${row.timestamp}-${msgKey}.eml").withOutputStream { os -> 
            msg.writeTo(os) 
        } 
        println " no= $no"
        no+= 1 
    }
} catch (Exception e) {
    println "Error: global-func-level: $e"
    e.printStackTrace();
} finally {
    if(sql) { sql.close() }
    if(sqlBody) { sqlBody.close() }
}
} //eo exportEmailMessages()

/** getBodyFilesTextHtml()\2  function  //MA 
   @return: arroy of Body FileText and FileHtml 
 */
def getBodyFilesTextHtml(final String sFilesDir, final long messageId)
        throws FileNotFoundException 
{
    // Groovy: has no integer divisoin operator "/", use i.intdiv(j) instead.
    long l1 = messageId.intdiv(100) % 100; //Java: long l1 = messageId / 100 % 100;
    long l2 = messageId % 100;

    final File dir = new File(sFilesDir+ "body/" + Long.toString(l1) + "/" + Long.toString(l2) + "/");
    if (!dir.isDirectory() ) {
        throw new FileNotFoundException("Could not open directory for body file");
    }
    textFile= new File(dir, Long.toString(messageId) + ".txt");
    htmlFile= new File(dir, Long.toString(messageId) + ".html");
    return [textFile, htmlFile];
}


/** //From: D:\Data-Windows10\Email-android--com-src\Email-nougat-mr1.2-release\ 
    //        \provider_src\com\android\email\provider\EmailProvider.java
 * Returns a {@link java.io.File} object pointing to the body content file for the message
 *
 * @param c Context for finding files dir
 * @param messageId id of message to locate
 * @param ext "html" or "txt"
 * @return File ready for operating upon
 */
File getBodyFile(final String sFilesDir, final long messageId, final String ext)
        throws FileNotFoundException {
    if (ext != "html"  && ext !=  "txt") {
        throw new IllegalArgumentException("ext must be one of 'html' or 'txt'");
    }
    long l1 = messageId / 100 % 100;
    long l2 = messageId % 100;
    // c.getFilesDir() ->replaced by: sFilesDir
    final File dir = new File(sFilesDir, 
      "body/" + Long.toString(l1) + "/" + Long.toString(l2) + "/");
    if (!dir.isDirectory() && !dir.mkdirs()) {
        throw new FileNotFoundException("Could not create directory for body file");
    }
    return new File(dir, Long.toString(messageId) + "." + ext);
}

/*
\Email-nougat-mr1.2-release\provider_src\com\android\email\provider\DBHelper.java  37% 
    private static void upgradeBodyFromVersion100ToVersion101(final Context context,
*/    


/** list accounts/mailboxes from an Android ADB email dump.
 */
def listEmailAccounts() 
{ 
    // connection handle for finally block
    def sql 
    try {
        // get the DB
        sql = Sql.newInstance("jdbc:sqlite:${path_email_db}/EmailProvider.db", 'org.sqlite.JDBC')
        
        // Print mail accounts list
        accList = []
        println("E-mail accounts list:")
        sql.eachRow("""
        SELECT a.displayName as accountName, a._id as accountKey, a.emailAddress as accountEmail
        FROM Account a   
        """) { row -> 
            println "Account: {name='${row.accountName}', accountID=${row.accountKey}, email='${row.accountEmail}'}"
    
            // Print mailboxes list = Folders in this Email account.
            println("Folders:")  
            sql.eachRow("""
            SELECT m.accountKey, m._id as mailboxKey, m.displayName as mailboxName
            FROM Mailbox m
            WHERE m.accountKey = ${row.accountKey}
            """) { mbxrow -> 
                println " mailbox='${mbxrow.mailboxName}', mailboxID:${mbxrow.mailboxKey}"
            } 
        }
        /* / Print mailboxes list 
        println("E-mail folders list per mail account:")
        sql.eachRow("""
        SELECT a.displayName as accountName, m.accountKey, m._id as mailboxKey, m.displayName as mailboxName
        FROM Account a, Mailbox m
        WHERE m.accountKey = a._id
        """) { row -> 
            println " '${row.accountName}' (accountID: ${row.accountKey}) .mailbox= '${row.mailboxName}' (mailboxID: ${row.mailboxKey})"
        } */ 
    } catch (Exception e) {
        println e
    } finally {
        if(sql) { sql.close() }
    }
}//eo listEmailAccounts

def print_Usage() {
  println "AndroidEmailExporter v0.1.0, <Main MENU>\n"+
          "Select data and operation:\n"+
          " Select-Data-Input: mail account (Default: a_min1), \n" +
          "                    mail folder (Default: INBOX), \n" +
          " Select-Operation: View \n" + 
          "                   Export: select-out-format: *.eml files list \n" +
          "                                              mbox file\n"
}

/** Ask user to Input operation and params
    @return params: [operation,accountID, mailboxID, outformat]
 */
def readOperationWithParams() {  
  println ""
  // https://stackoverflow.com/questions/10184091/groovy-console-read-input
  def operation = System.console().readLine "Enter operation: 1.Export AOSP Email client app data 2. Export Other mail data: "
  if (operation !="1" ) {
    println "Error: Only operation:1 is supported. Aborting."
    return [null, null, null, null]
  }
  // SO:What is the difference between defining variables using def and without?
  //https://stackoverflow.com/questions/39514795/what-is-the-difference-between-defining-variables-using-def-and-without/39546627
  def accountID = System.console().readLine "Enter accountID: "
  def mailboxID = System.console().readLine "Enter mailboxID: "
  def outformat = System.console().readLine "Enter Output format: 1 *.eml files in directory 2. a mbox file : "
  accountID= Integer.valueOf(accountID )
  mailboxID= Integer.valueOf(mailboxID )
  outformat= Integer.valueOf(outformat )
  if (outformat !=1 ) {
    println "Error: Only Output format:1 is supported. Aborting."
    return [null, null, null, null]
  } 
  println "Selected: operation=$operation data=(accountID=$accountID,mailboxID=$mailboxID) \n"+
          "  outformat=$outformat out-filename=$path_output_emls" 
  
  return [operation,accountID, mailboxID, outformat]
}

/** */
def main() {
 /* 
  print_Usage();
  listEmailAccounts(); //(path_email_db); 
  def (operation,accountID, mailboxID, outformat) = readOperationWithParams()
  if ( [operation,accountID, mailboxID, outformat] == [null, null, null, null] )
    //user input returned aborting results.
    return; 
  //-def (g_accountKey, g_mailboxKey)= [null, null]
  // Processing:
*/
  //exportEmailMessages(g_accountKey, g_mailboxKey) //for tests
  (operation,accountID, mailboxID, outformat) = [1,1,1,1] // [1,1,5,1]=works OK 
  cnt = getMailboxMessagesCount(accountID, mailboxID)
  println "Mailbox messages count= " + cnt.toString() 
  exportEmailMessages(accountID, mailboxID, -71, 71) //-60,10) //-110, 60)  
   //pretests:-150,1//-250,1)  //, outformat)
/*  println "Exporting: Done."
 */ 
}

main()

//--- 
def testInternetAddress(){
 se = "??? ????????? <info@chormarketing.com>"
 //println convertAddresses(se) 
 println convertAddresses(se)[0].toUnicodeString()
}
//testInternetAddress()

def sqlite_version() {
  sql= "PRAGMA schema_version;"
     sql = Sql.newInstance("jdbc:sqlite:${path_email_db}EmailProvider.db", 'org.sqlite.JDBC')
   // https://stackoverflow.com/questions/22684993/how-to-get-row-count-in-groovy-sql
   //def cnt = sql.firstRow('SELECT COUNT(*) AS cnt FROM form_tbl WHERE form_id=:id', id:1)?.cnt
   long schema_version = sql.firstRow("PRAGMA schema_version;")?.schema_version 
   println "schema_version: "+schema_version 
   return schema_version;
}
//sqlite_version() //returned: 41, same as dBeaver... //can't catch error with decoding subjects w new emojies from Netology

/* //
SELECT _id as msgKey, timeStamp, subject, messageId, fromList, displayName, toList, ccList, bccList, replyToList 
     FROM Message 
     WHERE accountKey = 1 and mailboxKey = 1 
     ORDER BY timeStamp DESC
     limit 110
*/

