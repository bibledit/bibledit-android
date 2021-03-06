/*
 Copyright (©) 2003-2021 Teus Benschop.
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.
 
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */


#ifndef INCLUDED_SENDRECEIVE_NOTES_H
#define INCLUDED_SENDRECEIVE_NOTES_H


#include <config/libraries.h>


string sendreceive_notes_sendreceive_text ();
string sendreceive_notes_up_to_date_text ();
void sendreceive_notes ();
bool sendreceive_notes_upload ();
bool sendreceive_notes_download (int lowId, int highId);
void sendreceive_notes_kick_watchdog ();


#endif
