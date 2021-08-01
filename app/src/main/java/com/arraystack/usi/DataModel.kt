package com.arraystack.usi

class DataModel {
    var title: String? = null
    var description: String? = null
    var imageLink: String? = null

    constructor(){}

    constructor(title: String?, description: String?, imageLink: String?) {
        this.title = title
        this.description = description
        this.imageLink = imageLink
    }
}