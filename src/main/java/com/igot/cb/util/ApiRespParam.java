package com.igot.cb.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class ApiRespParam {
    private String resMsgId;
    private String msgId;
    private String err;
    private String status;
    private String errMsg;

    public ApiRespParam(String id) {
        resMsgId = id;
        msgId = id;
    }

}
