package dev.settle.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoControlCharactersValidator implements ConstraintValidator<NoControlCharacters, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.chars().noneMatch(Character::isISOControl);
    }
}
