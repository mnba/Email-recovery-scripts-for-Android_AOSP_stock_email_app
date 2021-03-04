/**
 * Copying and distribution of this file, with or without modification,
 * are permitted in any medium without royalty provided the copyright
 * notice and this notice are preserved.  This file is offered as-is,
 * without any warranty.
 *
 * Script to list accounts/mailboxes from an Android ADB email dump.
 *
 * @author Robin Bramley (c) 2015
 */

@Grapes([
 @Grab(group='org.xerial',module='sqlite-jdbc',version='3.7.2'),
 @GrabConfig(systemClassLoader=true)
])

import java.sql.*

import groovy.sql.Sql

import org.sqlite.SQLite


// connection handle for finally block
def sql

try {
    // get the DB
    sql = Sql.newInstance('jdbc:sqlite:EmailProvider.db', 'org.sqlite.JDBC')
    
    // get the headers
    sql.eachRow("""
    SELECT a.displayName as accountName, m.accountKey, m._id as mailboxKey, m.displayName as mailboxName
    FROM Account a, Mailbox m
    WHERE m.accountKey = a._id
    """) { row -> 
        println "Account '${row.accountName}' (ID: ${row.accountKey}) contains mailbox '${row.mailboxName}' (ID: ${row.mailboxKey})"
    }
} catch (Exception e) {
    println e
} finally {
    if(sql) { sql.close() }
}