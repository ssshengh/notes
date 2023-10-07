package com.ss.example

class LinuxShowFileCmd: ShowFileCmd {
    override fun showFileCmd(): String {
        return "ls"
    }
}