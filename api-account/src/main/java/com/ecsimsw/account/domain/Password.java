package com.ecsimsw.account.domain;

import com.ecsimsw.error.UserException;
import com.ecsimsw.common.support.PasswordUtils;
import com.ecsimsw.common.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
//import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@NoArgsConstructor
@Embeddable
public class Password {

    @Column(name = "password")
    private String encrypted;
    private String tempPassword = null;
//
//    private Password(PasswordEncoder encoder, String purePassword, boolean isTemporary) {
//        if (purePassword.length() < 5 || purePassword.length() > 20) {
//            throw new UserException(ErrorType.INVALID_PASSWORD);
//        }
//        if (isTemporary) {
//            this.tempPassword = purePassword;
//        }
//        this.encrypted = encoder.encode(purePassword);
//    }
//
//    public static Password createRandomly(PasswordEncoder encoder) {
//        var tempPassword = PasswordUtils.generateRandom(15);
//        return new Password(encoder, tempPassword, true);
//    }
//
//    public static Password encode(PasswordEncoder encoder, String password) {
//        return new Password(encoder, password, false);
//    }

    public boolean isTempPassword() {
        return this.tempPassword != null && !this.tempPassword.isEmpty();
    }
}
