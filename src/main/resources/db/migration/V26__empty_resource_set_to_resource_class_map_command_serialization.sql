-- Fix for V21__resource_set_to_resource_class_map_command_serialization

UPDATE commandaudit
  SET command = replace(command,
                      '<resourceSet></resourceSet>',
                      '<resourceClasses>\n    <class name="RIPE"></class>\n  </resourceClasses>')
  WHERE commandtype = 'UpdateCertificateAuthorityResourcesCommand';
