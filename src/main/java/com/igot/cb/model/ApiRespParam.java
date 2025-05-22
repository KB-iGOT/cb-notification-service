package com.igot.cb.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



@Setter
@Getter
@NoArgsConstructor
public class ApiRespParam {

    private String resmsgid;
    private String msgid;
    private String err;
    private String status;
    private String errmsg;

    public ApiRespParam(String id) {
        resmsgid = id;
        msgid = id;
    }
}
