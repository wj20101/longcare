package com.ytone.longcare.features.home.reporting

import com.ytone.longcare.model.LoginLogParamModel

interface HomeLoginLogInfoProvider {
    fun build(): LoginLogParamModel
}
