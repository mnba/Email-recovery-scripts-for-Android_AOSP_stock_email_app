/**
 */
import javax.mail.internet.*;
import javax.mail.*;
public class MailTest {

  public static void main( String[] args ) throws Exception {
    Session mailSession = Session.getInstance( new Properties() );
    Transport transport = mailSession.getTransport();

    String text = "Hello, World";
    String html = "<h1>" + text + "</h1>";

    MimeMessage message = new MimeMessage( mailSession );
    Multipart multipart = new MimeMultipart( "alternative" );

    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setText( text, "utf-8" );

    MimeBodyPart htmlPart = new MimeBodyPart();
    htmlPart.setContent( html, "text/html; charset=utf-8" );

    multipart.addBodyPart( textPart );
    multipart.addBodyPart( htmlPart );
    message.setContent( multipart );

    // Unexpected output.
    System.out.println( "HTML = text/html : " + htmlPart.isMimeType( "text/html" ) );
    System.out.println( "HTML Content Type: " + htmlPart.getContentType() );

    // Required magic (violates principle of least astonishment).
    message.saveChanges();

    // Output now correct.    
    System.out.println( "TEXT = text/plain: " + textPart.isMimeType( "text/plain" ) );
    System.out.println( "HTML = text/html : " + htmlPart.isMimeType( "text/html" ) );
    System.out.println( "HTML Content Type: " + htmlPart.getContentType() );
    System.out.println( "HTML Data Handler: " + htmlPart.getDataHandler().getContentType() );
  }
}