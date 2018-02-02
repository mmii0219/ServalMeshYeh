/*
Copyright (C) 2014 Serval Project Inc.
Android specific functions that we don't need on other platforms

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#include <stdio.h>
#include "log.h"
#include "conf.h"

// We don't want to call any at_exit functions from the dalvik VM
void _exit(int status);
void exit(int status)
{
  if (config.debug.verbose)
    DEBUGF("Calling _exit(%d)", status);
  fflush(stdout);
  _exit(status);
}