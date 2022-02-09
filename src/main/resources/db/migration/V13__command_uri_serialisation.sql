-- This migration script replaces XStream 1.3.x serialization of java.net.URI format:
--
--          <java.net.URI serialization="custom">
--            <java.net.URI>
--              <default>
--                <string>rsync://rpki.ripe.net/ta/-SndBLVaDchzm60h4WPrfNiVu5k.cer</string>
--              </default>
--            </java.net.URI>
--          </java.net.URI>
--
-- into XStream 1.4.x URI format:
--
--          <uri>rsync://rpki.ripe.net/ta/-SndBLVaDchzm60h4WPrfNiVu5k.cer</uri>
--
-- using regular expression replacements.
--
-- Only the GenerateKeyPairCommand and ProcessOfflineResponseCommand seems to be affected.

update commandaudit
   set command = regexp_replace(command,
           E'\\<java\\.net\\.URI serialization="custom"\\>.+?\\<string\\>([^<]+)\\</string\\>.+?\\</java\\.net\\.URI\\>.+?\\</java\\.net\\.URI\\>',
           E'<uri>\\1</uri>',
           'g') -- Replace all occurrences
 where command like '%java.net.URI%';
update commandaudit
   set command = regexp_replace(command,
           E'\\<certificateRepositoryLocation serialization="custom"\\>.+?\\<string\\>([^<]+)\\</string\\>.+?\\</java\\.net\\.URI\\>.+?\\</certificateRepositoryLocation>',
           E'<certificateRepositoryLocation>\\1</certificateRepositoryLocation>',
           'g') -- Replace all occurrences
 where command like '%java.net.URI%';
