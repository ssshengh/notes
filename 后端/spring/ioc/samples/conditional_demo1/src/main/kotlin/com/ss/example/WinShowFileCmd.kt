package com.ss.example

class WinShowFileCmd: ShowFileCmd {
    override fun showFileCmd(): String {
        return "dir"
    }
}