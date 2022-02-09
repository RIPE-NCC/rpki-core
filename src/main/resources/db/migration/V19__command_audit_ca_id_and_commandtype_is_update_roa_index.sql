CREATE index commandaudit_ca_id_and_commandtype_is_update_roa ON commandaudit(ca_id, commandtype) WHERE (commandtype='UpdateRoaConfigurationCommand');
