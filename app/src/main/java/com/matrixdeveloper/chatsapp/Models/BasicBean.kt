package com.matrixdeveloper.chatsapp.Models

open class BasicBean : BaseBean() {


    /* private ArrayList<CountryBean> countries;
    private ArrayList<StateBean> states;
    private ArrayList<DistrictBean> cities;
    private ArrayList<CourtBean> courts;*/

    var requestID: String= ""
    var serviceID: String= ""
    var serviceStatus: Int = 0
    var otpCode: String= ""
    var phone: String= ""

    var countryID: Int = 0
    var stateID: Int = 0
    var districtID: Int = 0

    var isDriverOnline: Boolean = false
    var isPhoneAvailable: Boolean = false
    var statusCode: String =""

}
