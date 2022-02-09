<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="text" />

    <xsl:template match="/commands.SubscribeToRoaAlertCommand">
        <xsl:text>Subscribed </xsl:text>
        <xsl:value-of select="email"/>
        <xsl:text> to ROA alerts.</xsl:text>
    </xsl:template>

</xsl:stylesheet>