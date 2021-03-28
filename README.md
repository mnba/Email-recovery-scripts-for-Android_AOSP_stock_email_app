## Description
Scripts/app to Recover Android email app email messages, if they were downloaded onto mobile, and remained in your Android smartphone, in the AOSP/open-source stock email app.
This will save your lost emails if they still there.
This command-line app can Restore lost emails, it takes them from [Android stock email app] database and related files.
EML-file-extractor: It extracts email messages into .eml-format files, which is concidered as most interchangable format as it is RFC2844/RFC822 format files[2].

This set of Groovy scripts, downloads email messages, ("emails") .eml files from the database of (older) 
Google Android AOSP stock email app database.

That was the main Email app before Gmail-app came and started to block the work of original Email-app.

## Usage
The main program is `AndroidEmailExtractor.groovy`.

Simple usage examples can be found in runner.sh and other .sh/.bat scripts.

But for real-life use you will need manually edit the end of file `AndroidEmailExtractor.groovy` as it is the place of main instructions.


[2] RFC 2822 - Internet Message Format https://tools.ietf.org/html/rfc2822

