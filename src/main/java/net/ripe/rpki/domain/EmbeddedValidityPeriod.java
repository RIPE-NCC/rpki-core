/**
 * The BSD License
 *
 * Copyright (c) 2010, 2011 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.domain;

import lombok.Getter;
import net.ripe.rpki.commons.crypto.ValidityPeriod;
import net.ripe.rpki.commons.util.EqualsSupport;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * JPA mappable version of a ValidityPeriod
 */
@Embeddable
public class EmbeddedValidityPeriod extends EqualsSupport implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "validity_not_before", nullable = true)
    @Getter
    private DateTime notValidBefore;

    @Column(name = "validity_not_after", nullable = true)
    @Getter
    private DateTime notValidAfter;

    protected EmbeddedValidityPeriod() {
    }

    public EmbeddedValidityPeriod(ValidityPeriod period) {
        Validate.notNull(period);
        this.notValidBefore = period.getNotValidBefore();
        this.notValidAfter = period.getNotValidAfter();
    }
    
    public ValidityPeriod toValidityPeriod() {
        return new ValidityPeriod(getNotValidBefore(), getNotValidAfter());
    }
    
    @Override
    public String toString() {
        return getNotValidBefore() + " - " + getNotValidAfter();
    }

}
