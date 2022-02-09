<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xslt="http://xml.apache.org/xslt">
    <xsl:output method="text" />

    <xsl:template match="/commands.UpdateRoaConfigurationCommand">
        <xsl:text>Updated ROA configuration. Additions: </xsl:text>
        <xsl:if test="number(count(additions/RoaPrefixConfiguration)) = 0">
            <xsl:text>none</xsl:text>
        </xsl:if>
        <xsl:for-each select="additions/RoaPrefixConfiguration">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:text>[asn=</xsl:text>
            <xsl:value-of select="asn"/>
            <xsl:text>, prefix=</xsl:text>
            <xsl:value-of select="prefix"/>
            <xsl:text>, maximumLength=</xsl:text>
            <xsl:choose>
                <xsl:when test="maximumLength">
                    <xsl:value-of select="maximumLength"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="substring-after(prefix, '/')"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>]</xsl:text>
        </xsl:for-each>
        <xsl:text>. Deletions: </xsl:text>
        <xsl:if test="number(count(deletions/RoaPrefixConfiguration)) = 0">
            <xsl:text>none</xsl:text>
        </xsl:if>
        <xsl:for-each select="deletions/RoaPrefixConfiguration">
            <xsl:if test="number(position()) &gt; 1">
                <xsl:text>, </xsl:text>
            </xsl:if>
            <xsl:text>[asn=</xsl:text>
            <xsl:value-of select="asn"/>
            <xsl:text>, prefix=</xsl:text>
            <xsl:value-of select="prefix"/>
            <xsl:text>, maximumLength=</xsl:text>
            <xsl:choose>
                <xsl:when test="maximumLength">
                    <xsl:value-of select="maximumLength"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="substring-after(prefix, '/')"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>]</xsl:text>
        </xsl:for-each>
        <xsl:text>.</xsl:text>
    </xsl:template>

</xsl:stylesheet>