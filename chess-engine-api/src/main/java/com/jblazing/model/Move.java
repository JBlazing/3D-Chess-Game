package com.jblazing.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * @param promotion piece a pawn promotes to (q, r, b or n); null when not promoting,
 *                  in which case a promotion defaults to a queen
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record Move(String from, String to, String promotion) {

    public Move(String from, String to) {
        this(from, to, null);
    }
}
